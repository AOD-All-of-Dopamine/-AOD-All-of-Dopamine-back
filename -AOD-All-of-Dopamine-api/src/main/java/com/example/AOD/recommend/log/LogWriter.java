package com.example.AOD.recommend.log;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 로그 적재 스레드 (REC_TAB_DESIGN §5-8).
 * - 1초 또는 200건마다 JDBC 배치 INSERT, 전용 풀(recLogJdbcTemplate) 사용
 * - DedupedLogRecord 는 event_seen 을 먼저 통과시켜 새 것만 본 테이블에 넣는다
 * - 쓰기 실패는 배치를 버리고 센다. 추천·반응 응답에 영향을 주지 않는다
 * - 종료 시 최대 10초 동안 큐를 비운다
 */
@Slf4j
@Component
public class LogWriter implements SmartLifecycle {

    static final int BATCH_MAX = 200;
    static final long POLL_MS = 1_000;
    static final long SHUTDOWN_DRAIN_MS = 10_000;
    static final String EVENT_SEEN_SQL =
            "INSERT INTO aod_log.event_seen (event_id, server_ts) VALUES (?, ?) ON CONFLICT (event_id) DO NOTHING";

    private final LogQueue queue;
    private final JdbcTemplate jdbc;
    private final TransactionOperations tx;
    private final boolean threadEnabled;
    private final Counter written;
    private final Counter duplicates;
    private final Counter failed;
    private final Object writeLock = new Object();

    private volatile boolean running;
    private volatile Thread thread;

    @Autowired
    public LogWriter(LogQueue queue, RecLogJdbc recLogJdbc, MeterRegistry registry,
                     @Value("${rec.log.writer.enabled:true}") boolean threadEnabled) {
        this(queue, recLogJdbc.jdbc(), recLogJdbc.tx(), registry, threadEnabled);
    }

    LogWriter(LogQueue queue, JdbcTemplate jdbc, TransactionOperations tx, MeterRegistry registry, boolean threadEnabled) {
        this.queue = queue;
        this.jdbc = jdbc;
        this.tx = tx;
        this.threadEnabled = threadEnabled;
        this.written = Counter.builder("rec.log.written").description("적재한 추천 로그 행 수").register(registry);
        this.duplicates = Counter.builder("rec.log.duplicates").description("event_seen 에서 걸러진 중복 이벤트 수").register(registry);
        this.failed = Counter.builder("rec.log.failed").description("쓰기 실패로 버린 추천 로그 행 수").register(registry);
    }

    /**
     * 배치 1개를 한 트랜잭션으로 쓴다. event_seen 과 본 테이블이 따로 커밋되면, 본 테이블 쓰기가 실패했을 때
     * 그 이벤트가 "이미 본 것"으로 남아 재전송까지 영구히 걸러진다 — 그래서 함께 커밋하거나 함께 롤백한다.
     * 예외는 호출자(safeWrite)가 처리한다. 카운터는 커밋된 뒤에만 올린다.
     */
    void write(List<LogRecord> batch) {
        int[] tally = new int[2];   // [0] 쓴 행, [1] 중복으로 거른 행
        synchronized (writeLock) {
            tx.executeWithoutResult(status -> {
                tally[0] = 0;
                tally[1] = 0;
                List<LogRecord> survivors = new ArrayList<>(batch.size());
                List<DedupedLogRecord> deduped = new ArrayList<>();
                for (LogRecord r : batch) {
                    if (r instanceof DedupedLogRecord d) deduped.add(d); else survivors.add(r);
                }
                if (!deduped.isEmpty()) {
                    int[] counts = jdbc.batchUpdate(EVENT_SEEN_SQL, new BatchPreparedStatementSetter() {
                        @Override public void setValues(PreparedStatement ps, int i) throws SQLException {
                            ps.setObject(1, deduped.get(i).eventId());
                            ps.setObject(2, deduped.get(i).serverTs());
                        }
                        @Override public int getBatchSize() { return deduped.size(); }
                    });
                    for (int i = 0; i < deduped.size(); i++) {
                        // 0 = 충돌(중복). SUCCESS_NO_INFO(-2) 는 판별 불가라 통과시킨다 —
                        // JDBC URL 에 reWriteBatchedInserts=true 를 켜면 전부 -2 가 되어 중복 제거가 무력해지니 켜지 않는다.
                        if (counts == null || i >= counts.length || counts[i] != 0) survivors.add(deduped.get(i));
                        else tally[1]++;
                    }
                }
                Map<String, List<LogRecord>> bySql = new LinkedHashMap<>();
                for (LogRecord r : survivors) bySql.computeIfAbsent(r.sql(), k -> new ArrayList<>()).add(r);
                for (Map.Entry<String, List<LogRecord>> e : bySql.entrySet()) {
                    List<LogRecord> rows = e.getValue();
                    jdbc.batchUpdate(e.getKey(), new BatchPreparedStatementSetter() {
                        @Override public void setValues(PreparedStatement ps, int i) throws SQLException { rows.get(i).bind(ps); }
                        @Override public int getBatchSize() { return rows.size(); }
                    });
                    tally[0] += rows.size();
                }
            });
        }
        written.increment(tally[0]);
        duplicates.increment(tally[1]);
    }

    /** 큐에 남은 것을 호출 스레드에서 전부 쓴다 (테스트·종료용). 돌려주는 값은 큐에서 꺼낸 행 수. */
    public int flushNow() {
        int total = 0;
        List<LogRecord> batch = new ArrayList<>(BATCH_MAX);
        while (queue.drainTo(batch, BATCH_MAX) > 0) {
            total += batch.size();
            safeWrite(batch);
            batch = new ArrayList<>(BATCH_MAX);
        }
        return total;
    }

    private void safeWrite(List<LogRecord> batch) {
        try {
            write(batch);
        } catch (Exception e) {
            failed.increment(batch.size());
            log.error("추천 로그 배치 쓰기 실패 — {}행 버림", batch.size(), e);
        }
    }

    private void loop() {
        while (running) {
            try {
                LogRecord first = queue.poll(POLL_MS, TimeUnit.MILLISECONDS);
                if (first == null) continue;
                List<LogRecord> batch = new ArrayList<>(BATCH_MAX);
                batch.add(first);
                queue.drainTo(batch, BATCH_MAX - 1);
                safeWrite(batch);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    @Override
    public void start() {
        if (!threadEnabled || running) return;
        running = true;
        thread = new Thread(this::loop, "rec-log-writer");
        thread.setDaemon(true);
        thread.start();
    }

    @Override
    public void stop() {
        running = false;
        if (thread != null) {
            try {
                thread.join(POLL_MS + 2_000);   // poll 이 1초 안에 돌아오므로 interrupt 없이 기다린다
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        long deadline = System.currentTimeMillis() + SHUTDOWN_DRAIN_MS;
        List<LogRecord> batch = new ArrayList<>(BATCH_MAX);
        while (System.currentTimeMillis() < deadline && queue.drainTo(batch, BATCH_MAX) > 0) {
            safeWrite(batch);
            batch = new ArrayList<>(BATCH_MAX);
        }
        if (queue.size() > 0) log.warn("종료 flush 시간 초과 — 추천 로그 {}행 유실", queue.size());
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    /**
     * 기본 phase(Integer.MAX_VALUE)면 웹 서버보다 먼저 멈춰서, 종료 중 들어온 요청의 로그가 flush 뒤에 쌓여 버려진다.
     * 0 으로 낮춰 웹 서버가 멈춘 뒤에 멈추게 한다 (phase 가 낮을수록 나중에 stop).
     */
    @Override
    public int getPhase() {
        return 0;
    }
}
