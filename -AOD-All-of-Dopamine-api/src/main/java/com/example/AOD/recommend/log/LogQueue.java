package com.example.AOD.recommend.log;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/** 로그 메모리 큐. 요청 스레드는 offer 만 한다 — 가득 차면 버리고 센다 (REC_TAB_DESIGN §5-8). */
@Component
public class LogQueue {

    static final int DEFAULT_CAPACITY = 10_000;

    private final BlockingQueue<LogRecord> queue;
    private final Counter dropped;

    @Autowired
    public LogQueue(MeterRegistry registry) {
        this(registry, DEFAULT_CAPACITY);
    }

    LogQueue(MeterRegistry registry, int capacity) {
        this.queue = new LinkedBlockingQueue<>(capacity);
        this.dropped = Counter.builder("rec.log.dropped")
                .description("큐가 가득 차 버린 추천 로그 행 수")
                .register(registry);
        Gauge.builder("rec.log.queue.size", queue, BlockingQueue::size)
                .description("적재를 기다리는 추천 로그 행 수")
                .register(registry);
    }

    public boolean offer(LogRecord record) {
        if (queue.offer(record)) return true;
        dropped.increment();
        return false;
    }

    LogRecord poll(long timeout, TimeUnit unit) throws InterruptedException {
        return queue.poll(timeout, unit);
    }

    /** 다른 패키지의 테스트와 후속 하위 프로젝트도 쓰므로 public. */
    public int drainTo(Collection<? super LogRecord> out, int max) {
        return queue.drainTo(out, max);
    }

    public int size() {
        return queue.size();
    }
}
