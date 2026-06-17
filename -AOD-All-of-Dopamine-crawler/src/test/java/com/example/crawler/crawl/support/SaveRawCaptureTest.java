package com.example.crawler.crawl.support;

import com.example.crawler.ingest.CollectorService;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SaveRawCaptureTest {

    @Test
    void capturesAllFiveArgumentsAndRendersCanonicalJson() {
        CollectorService collector = mock(CollectorService.class);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", "Portal");
        collector.saveRaw("Steam", "GAME", payload, "400", "https://store.steampowered.com/app/400");

        SaveRawCapture capture = SaveRawCapture.from(collector);

        assertThat(capture.platformName).isEqualTo("Steam");
        assertThat(capture.domain).isEqualTo("GAME");
        assertThat(capture.platformSpecificId).isEqualTo("400");
        assertThat(capture.url).isEqualTo("https://store.steampowered.com/app/400");
        assertThat(capture.payload).containsEntry("title", "Portal");
        assertThat(capture.toCanonicalJson())
                .contains("\"platformName\" : \"Steam\"")
                .contains("\"title\" : \"Portal\"");
    }
}
