package com.example.crawler.monitoring;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class CustomMetrics {

    private final Counter crawlerSuccessCounter;
    private final Counter crawlerFailureCounter;
    private final Timer crawlerDurationTimer;
    private final MeterRegistry meterRegistry;

    public CustomMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;

        this.crawlerSuccessCounter = Counter.builder("crawler.success")
                .description("크롤링 성공 횟수")
                .tag("type", "total")
                .register(meterRegistry);

        this.crawlerFailureCounter = Counter.builder("crawler.failure")
                .description("크롤링 실패 횟수")
                .tag("type", "total")
                .register(meterRegistry);

        this.crawlerDurationTimer = Timer.builder("crawler.duration")
                .description("크롤링 소요 시간")
                .tag("type", "execution")
                .register(meterRegistry);
    }

    public void recordCrawlerSuccess(String platform) {
        crawlerSuccessCounter.increment();
        Counter.builder("crawler.success.by.platform")
                .description("플랫폼별 크롤링 성공")
                .tag("platform", platform)
                .register(meterRegistry)
                .increment();
    }

    public void recordCrawlerFailure(String platform, String reason) {
        crawlerFailureCounter.increment();
        Counter.builder("crawler.failure.by.platform")
                .description("플랫폼별 크롤링 실패")
                .tag("platform", platform)
                .tag("reason", reason)
                .register(meterRegistry)
                .increment();
    }

    public Timer.Sample startTimer() {
        return Timer.start(meterRegistry);
    }

    public void recordDuration(Timer.Sample sample, String platform) {
        sample.stop(Timer.builder("crawler.duration.by.platform")
                .description("플랫폼별 크롤링 소요 시간")
                .tag("platform", platform)
                .register(meterRegistry));
    }

    public void recordItemsProcessed(String platform, int count) {
        meterRegistry.counter("crawler.items.processed",
                        "platform", platform)
                .increment(count);
    }
}

