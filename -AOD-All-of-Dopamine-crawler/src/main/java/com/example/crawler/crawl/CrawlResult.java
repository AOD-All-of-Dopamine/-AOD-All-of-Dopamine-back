package com.example.crawler.crawl;

/**
 * Outcome of a single CrawlPipeline.run(...).
 * Consumer mapping (later plan): SUCCESS -> COMPLETED, FAILED -> FAILED (retry),
 * SKIPPED -> COMPLETED (no retry — e.g. adult/non-game content).
 */
public record CrawlResult(Status status, String reason, Throwable error) {

    public enum Status {
        SUCCESS, SKIPPED, FAILED
    }

    public static CrawlResult success() {
        return new CrawlResult(Status.SUCCESS, null, null);
    }

    public static CrawlResult skipped(String reason) {
        return new CrawlResult(Status.SKIPPED, reason, null);
    }

    public static CrawlResult failed(Throwable error) {
        return new CrawlResult(Status.FAILED, error == null ? null : error.getMessage(), error);
    }

    public boolean isSuccess() {
        return status == Status.SUCCESS;
    }

    public boolean isSkipped() {
        return status == Status.SKIPPED;
    }

    public boolean isFailed() {
        return status == Status.FAILED;
    }
}
