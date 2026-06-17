package com.example.crawler.crawl;

import com.example.crawler.ingest.CollectorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * The single crawl runner. All cross-cutting concerns (validation, logging, saving, exception
 * handling, cleanup) live here exactly once, so each ContentSource only implements fetch + parse.
 *
 * Metrics are intentionally NOT here yet — they are added when CrawlMetrics is introduced in a
 * later plan (consumer rewire / metrics consolidation).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CrawlPipeline {

    private final CollectorService collector;

    public <D> CrawlResult run(ContentSource<D> source, String targetId) {
        SourceDescriptor d = source.descriptor();
        try {
            if (targetId == null || targetId.isBlank()) {
                log.warn("[{}/{}] blank targetId — skip", d.platformName(), d.domain());
                return CrawlResult.skipped("blank id");
            }

            D raw = source.fetchDetail(targetId);
            if (raw == null) {
                log.info("[{}/{}] id={} no detail — skip", d.platformName(), d.domain(), targetId);
                return CrawlResult.skipped("no detail");
            }

            CrawlPayload p = source.parse(targetId, raw);
            if (p == null) {
                log.info("[{}/{}] id={} filtered — skip", d.platformName(), d.domain(), targetId);
                return CrawlResult.skipped("filtered");
            }

            String url = (p.url() != null && !p.url().isBlank())
                    ? p.url()
                    : d.urlOf(p.platformSpecificId());

            collector.saveRaw(d.platformName(), d.domain(), p.payload(), p.platformSpecificId(), url);
            log.debug("[{}/{}] id={} saved", d.platformName(), d.domain(), targetId);
            return CrawlResult.success();

        } catch (Exception e) {
            log.error("[{}/{}] id={} crawl failed: {}",
                    d.platformName(), d.domain(), targetId, e.getMessage(), e);
            return CrawlResult.failed(e);
        } finally {
            try {
                source.cleanup();
            } catch (Exception ce) {
                log.warn("[{}/{}] cleanup failed: {}", d.platformName(), d.domain(), ce.getMessage());
            }
        }
    }
}
