package com.example.crawler.crawl;

/**
 * Uniform per-platform crawl contract. One implementation per platform×domain
 * (e.g. SteamGameSource, TmdbMovieSource, NaverSeriesNovelSource).
 *
 * @param <D> the platform-private raw detail type returned by fetchDetail and consumed by parse
 *            (e.g. Map for HTTP JSON, org.jsoup.nodes.Document for Jsoup, a DTO for Selenium).
 */
public interface ContentSource<D> {

    /** Metadata: platform, domain, jobType, url template, tuning. */
    SourceDescriptor descriptor();

    /** Fetch raw detail for one target id (HTTP / Jsoup / Selenium). May return null if unavailable. */
    D fetchDetail(String targetId);

    /** Convert raw detail into a CrawlPayload. Return null to SKIP (e.g. adult content, wrong type). */
    CrawlPayload parse(String targetId, D rawDetail);

    /** Optional resource cleanup (e.g. Selenium ThreadLocal driver). Called in the pipeline's finally. */
    default void cleanup() {
    }
}
