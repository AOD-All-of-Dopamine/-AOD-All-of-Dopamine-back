package com.example.crawler.common.queue;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * crawl_job_queue 작업 큐 메트릭 (Micrometer -> Prometheus).
 *
 * 노출 메트릭:
 * <ul>
 *   <li>{@code crawl_job_queue_size{status}}        - 상태별 큐 적재량 (30초마다 갱신되는 게이지)</li>
 *   <li>{@code crawl_job_completed_total{job_type}} - 성공 완료 누적 카운터</li>
 *   <li>{@code crawl_job_failed_total{job_type}}    - 실패 누적 카운터</li>
 *   <li>{@code crawl_job_duration_seconds{job_type}}- 작업 실행 시간(히스토그램)</li>
 * </ul>
 *
 * Grafana 대시보드/알람은 이 이름들을 그대로 참조한다.
 */
@Slf4j
@Component
public class CrawlJobMetrics {

    private final CrawlJobRepository crawlJobRepository;
    private final MeterRegistry meterRegistry;

    /** 상태별 큐 깊이 게이지 백킹 값 */
    private final Map<JobStatus, AtomicLong> queueSizeByStatus = new EnumMap<>(JobStatus.class);

    public CrawlJobMetrics(CrawlJobRepository crawlJobRepository, MeterRegistry meterRegistry) {
        this.crawlJobRepository = crawlJobRepository;
        this.meterRegistry = meterRegistry;

        // 상태별 큐 깊이 게이지를 모든 JobStatus에 대해 미리 등록 (값은 refreshQueueGauges가 갱신)
        for (JobStatus status : JobStatus.values()) {
            AtomicLong holder = new AtomicLong(0);
            queueSizeByStatus.put(status, holder);
            Gauge.builder("crawl.job.queue.size", holder, AtomicLong::get)
                    .description("상태별 크롤 작업 큐 적재량")
                    .tag("status", status.name())
                    .register(meterRegistry);
        }
    }

    /** 작업 실행 시간 측정 시작 */
    public Timer.Sample startTimer() {
        return Timer.start(meterRegistry);
    }

    /** 작업 성공 완료 기록 */
    public void recordCompleted(JobType jobType) {
        meterRegistry.counter("crawl.job.completed", "job_type", jobType.name()).increment();
    }

    /** 작업 실패 기록 */
    public void recordFailed(JobType jobType) {
        meterRegistry.counter("crawl.job.failed", "job_type", jobType.name()).increment();
    }

    /** 작업 실행 시간 기록 (job_type 태그) */
    public void recordDuration(Timer.Sample sample, JobType jobType) {
        sample.stop(Timer.builder("crawl.job.duration")
                .description("크롤 작업 실행 시간")
                .tag("job_type", jobType.name())
                .publishPercentileHistogram()
                .register(meterRegistry));
    }

    /**
     * 상태별 큐 깊이를 주기적으로 갱신하여 게이지에 반영한다.
     * (게이지 supplier에서 매 스크랩마다 DB count를 치지 않도록, 30초 주기로 한 번만 집계)
     */
    @Scheduled(fixedDelay = 30000, initialDelay = 5000)
    public void refreshQueueGauges() {
        try {
            // 통계에 빠진 상태는 0으로 보이도록 먼저 리셋
            queueSizeByStatus.values().forEach(h -> h.set(0));
            List<Object[]> stats = crawlJobRepository.getStatusStatistics();
            for (Object[] row : stats) {
                JobStatus status = (JobStatus) row[0];
                long count = ((Number) row[1]).longValue();
                AtomicLong holder = queueSizeByStatus.get(status);
                if (holder != null) {
                    holder.set(count);
                }
            }
        } catch (Exception e) {
            log.warn("crawl_job_queue_size 게이지 갱신 실패: {}", e.getMessage());
        }
    }
}
