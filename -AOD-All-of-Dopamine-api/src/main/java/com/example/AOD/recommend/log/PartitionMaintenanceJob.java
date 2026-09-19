package com.example.AOD.recommend.log;

import com.example.AOD.recommend.chain.ChainService;
import com.example.AOD.recommend.notinterested.NotInterestedService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * aod_log 월 파티션 유지 (REC_TAB_DESIGN §5-3·§5-9).
 * 매일: 이번 달 + 다음 2개월 파티션 보장 · 보관 기간 지난 파티션 DROP · 보조 테이블 행 정리 · aod_rec 서빙 상태 정리.
 * 파티션 생성이 부모 테이블에 잠깐 ACCESS EXCLUSIVE 잠금을 걸기 때문에 한국 새벽(KST) 스케줄로 돈다.
 */
@Slf4j
@Component
public class PartitionMaintenanceJob {

    record PartitionedTable(String name, int retentionMonths) { }

    static final List<PartitionedTable> TABLES = List.of(
            new PartitionedTable("rec_request", 12),
            new PartitionedTable("rec_item_served", 12),
            new PartitionedTable("event", 12),
            new PartitionedTable("client_agent", 3));
    static final int MONTHS_AHEAD = 2;
    static final int EVENT_SEEN_DAYS = 7;
    static final int REJECTED_EVENT_DAYS = 30;
    /** 보관 기간은 그 테이블을 소유한 서비스가 정한다 — 여기에 숫자를 또 적으면 한쪽만 바뀐다. */
    static final Duration REC_CHAIN_TTL = ChainService.TTL;
    static final Duration NOT_INTERESTED_TTL = NotInterestedService.TTL;

    private static final String CHILDREN_SQL =
            "SELECT c.relname FROM pg_inherits i "
          + "JOIN pg_class c ON c.oid = i.inhrelid "
          + "JOIN pg_class p ON p.oid = i.inhparent "
          + "JOIN pg_namespace n ON n.oid = p.relnamespace "
          + "WHERE n.nspname = 'aod_log' AND p.relname = ?";

    private final JdbcTemplate jdbc;
    private final Clock clock;

    @Autowired
    public PartitionMaintenanceJob(RecLogJdbc recLogJdbc) {
        this(recLogJdbc.jdbc(), Clock.systemUTC());
    }

    PartitionMaintenanceJob(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        runSafely();
    }

    // 파티션 생성은 부모 테이블에 잠깐 ACCESS EXCLUSIVE 잠금을 건다 → 한국 새벽에 돈다. 월 계산 자체는 UTC(clock) 기준.
    // 04:15 KST 는 UTC 로 전날 19:15 다. 매월 1일 실행분은 UTC 기준 "지난달"로 계산하지만, 2개월 앞까지 만들므로 빈틈이 없다.
    @Scheduled(cron = "0 15 4 * * *", zone = "Asia/Seoul")
    public void daily() {
        runSafely();
    }

    void runSafely() {
        try {
            Boolean schemaExists = jdbc.queryForObject(
                    "SELECT EXISTS (SELECT 1 FROM information_schema.schemata WHERE schema_name = 'aod_log')",
                    Boolean.class);
            if (!Boolean.TRUE.equals(schemaExists)) {
                log.warn("aod_log 스키마가 없어 파티션 유지 작업을 건너뜀 (V8 마이그레이션 전)");
                return;
            }
            List<String> created = ensureFuturePartitions();
            List<String> dropped = dropExpiredPartitions();
            purgeAuxTables();
            purgeRecTables();
            log.info("aod_log 파티션 유지 완료 — 보장 {}개 · DROP {}개 {}", created.size(), dropped.size(), dropped);
        } catch (Exception e) {
            log.error("aod_log 파티션 유지 작업 실패", e);
        }
    }

    /** 이번 달부터 MONTHS_AHEAD 개월 뒤까지의 파티션을 보장한다. 돌려주는 값은 보장한 파티션 이름. */
    public List<String> ensureFuturePartitions() {
        YearMonth now = YearMonth.now(clock);
        List<String> ensured = new ArrayList<>();
        for (PartitionedTable t : TABLES) {
            for (int i = 0; i <= MONTHS_AHEAD; i++) {
                YearMonth month = now.plusMonths(i);
                String partition = PartitionNames.of(t.name(), month);
                String sql = "CREATE TABLE IF NOT EXISTS aod_log." + partition + " PARTITION OF aod_log." + t.name()
                        + " FOR VALUES FROM ('" + PartitionNames.boundLiteral(month) + "')"
                        + " TO ('" + PartitionNames.boundLiteral(month.plusMonths(1)) + "')";
                try {
                    jdbc.execute(sql);
                    ensured.add(partition);
                } catch (Exception e) {
                    // DEFAULT 파티션에 그 달의 행이 있으면 PostgreSQL 이 거부한다 — 경보 대상(REC_TAB_DESIGN §11)
                    log.error("파티션 생성 실패: {} — DEFAULT 파티션에 행이 쌓였는지 확인", partition, e);
                }
            }
        }
        return ensured;
    }

    /** 보관 기간보다 오래된 월 파티션을 DROP 한다. 기준월 = 이번 달 - 보관 개월, 그보다 이전 달만 지운다. */
    public List<String> dropExpiredPartitions() {
        YearMonth now = YearMonth.now(clock);
        List<String> dropped = new ArrayList<>();
        for (PartitionedTable t : TABLES) {
            // isBefore 라서 기준월 자체는 남는다 — 실제 보관은 "보관 개월 + 진행 중인 달".
            YearMonth cutoff = now.minusMonths(t.retentionMonths());
            List<String> children = jdbc.queryForList(CHILDREN_SQL, String.class, t.name());
            for (String child : children) {
                boolean expired = PartitionNames.monthOf(t.name(), child).map(m -> m.isBefore(cutoff)).orElse(false);
                if (!expired) continue;
                jdbc.execute("DROP TABLE IF EXISTS aod_log." + child);
                dropped.add(child);
            }
        }
        return dropped;
    }

    /** 파티션이 아닌 보조 테이블은 행을 지운다. */
    public void purgeAuxTables() {
        OffsetDateTime now = OffsetDateTime.now(clock);
        jdbc.update("DELETE FROM aod_log.event_seen WHERE server_ts < ?", now.minusDays(EVENT_SEEN_DAYS));
        jdbc.update("DELETE FROM aod_log.rejected_event WHERE server_ts < ?", now.minusDays(REJECTED_EVENT_DAYS));
    }

    /**
     * 서빙 상태(aod_rec) 정리 — 24시간 지난 체인 · 90일 지난 관심 없음 (REC_TAB_DESIGN §5-3).
     * 같은 데이터베이스라 로그 풀로 지울 수 있다. DELETE 두 문장뿐이라 로그 풀(연결 2개)에 부담이 없고,
     * ChainService·NotInterestedService(주 풀)를 주입하면 로그 컴포넌트가 서빙 쪽에 묶이므로 SQL 은 직접 쓴다.
     * 다만 **보관 기간은 그 서비스의 상수를 그대로 참조한다** — 숫자를 여기 또 적으면 한쪽만 바뀐다.
     */
    public void purgeRecTables() {
        OffsetDateTime now = OffsetDateTime.now(clock);
        jdbc.update("DELETE FROM aod_rec.rec_chain WHERE updated_at < ?", now.minus(REC_CHAIN_TTL));
        jdbc.update("DELETE FROM aod_rec.not_interested WHERE created_at < ?", now.minus(NOT_INTERESTED_TTL));
    }
}
