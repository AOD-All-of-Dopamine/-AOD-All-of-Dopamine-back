package com.example.crawler.crawl;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CrawlResultTest {

    @Test
    void successHasNoReasonOrError() {
        CrawlResult r = CrawlResult.success();
        assertThat(r.status()).isEqualTo(CrawlResult.Status.SUCCESS);
        assertThat(r.isSuccess()).isTrue();
        assertThat(r.reason()).isNull();
        assertThat(r.error()).isNull();
    }

    @Test
    void skippedCarriesReason() {
        CrawlResult r = CrawlResult.skipped("filtered");
        assertThat(r.status()).isEqualTo(CrawlResult.Status.SKIPPED);
        assertThat(r.isSkipped()).isTrue();
        assertThat(r.reason()).isEqualTo("filtered");
    }

    @Test
    void failedCarriesErrorAndItsMessage() {
        RuntimeException boom = new RuntimeException("boom");
        CrawlResult r = CrawlResult.failed(boom);
        assertThat(r.status()).isEqualTo(CrawlResult.Status.FAILED);
        assertThat(r.isFailed()).isTrue();
        assertThat(r.error()).isSameAs(boom);
        assertThat(r.reason()).isEqualTo("boom");
    }
}
