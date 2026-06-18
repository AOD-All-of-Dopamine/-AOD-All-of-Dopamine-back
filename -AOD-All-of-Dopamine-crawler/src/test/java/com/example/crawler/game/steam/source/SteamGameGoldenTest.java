package com.example.crawler.game.steam.source;

import com.example.crawler.crawl.support.GoldenFiles;
import com.example.crawler.crawl.support.SaveRawCapture;
import com.example.crawler.game.steam.fetcher.SteamApiFetcher;
import com.example.crawler.game.steam.processor.SteamPayloadProcessor;
import com.example.crawler.game.steam.service.SteamCrawlService;
import com.example.crawler.ingest.CollectorService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.Map;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SteamGameGoldenTest {

    private static final long APP_ID = 400L;

    private Map<String, Object> loadFixture() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/fixtures/steam/app-400.json")) {
            return new ObjectMapper().readValue(in, new TypeReference<Map<String, Object>>() {});
        }
    }

    /**
     * Characterization of the CURRENT single-item path. Locks the saveRaw record as the golden.
     * Removed in Task 3 once collectGameByAppId is deleted — the golden then guards SteamGameSource.
     */
    @Test
    void legacyCollectGameByAppIdMatchesGolden() throws Exception {
        SteamApiFetcher fetcher = mock(SteamApiFetcher.class);
        CollectorService collector = mock(CollectorService.class);
        SteamPayloadProcessor processor = new SteamPayloadProcessor();
        when(fetcher.fetchGameDetails(eq(APP_ID))).thenReturn(loadFixture());

        SteamCrawlService legacy = new SteamCrawlService(fetcher, collector, processor);
        legacy.collectGameByAppId(APP_ID);

        GoldenFiles.assertMatchesGolden("steam-game-400.json",
                SaveRawCapture.from(collector).toCanonicalJson());
    }
}
