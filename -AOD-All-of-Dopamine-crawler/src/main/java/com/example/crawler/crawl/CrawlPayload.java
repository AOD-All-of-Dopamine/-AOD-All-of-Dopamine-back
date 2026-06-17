package com.example.crawler.crawl;

import java.util.Map;

/**
 * The output of ContentSource.parse(...): everything CollectorService.saveRaw needs.
 * url may be null — the pipeline then uses SourceDescriptor.urlOf(platformSpecificId).
 */
public record CrawlPayload(String platformSpecificId, String url, Map<String, Object> payload) {

    public CrawlPayload {
        if (platformSpecificId == null || platformSpecificId.isBlank()) {
            throw new IllegalArgumentException("platformSpecificId is required");
        }
        if (payload == null) {
            throw new IllegalArgumentException("payload is required");
        }
    }

    /** Convenience: url derived from the descriptor at pipeline time. */
    public static CrawlPayload of(String platformSpecificId, Map<String, Object> payload) {
        return new CrawlPayload(platformSpecificId, null, payload);
    }
}
