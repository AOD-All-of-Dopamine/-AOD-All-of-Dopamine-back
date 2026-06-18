package com.example.crawler.game.steam.source;

import com.example.crawler.crawl.CrawlPipeline;
import com.example.crawler.crawl.support.GoldenFiles;
import com.example.crawler.crawl.support.SaveRawCapture;
import com.example.crawler.game.steam.fetcher.SteamApiFetcher;
import com.example.crawler.game.steam.processor.SteamPayloadProcessor;
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
     * The new unified path (SteamGameSource via CrawlPipeline) must produce the SAME saveRaw
     * record as the legacy path — guarded by the same golden file.
     */
    @Test
    void steamGameSourceMatchesGolden() throws Exception {
        SteamApiFetcher fetcher = mock(SteamApiFetcher.class);
        CollectorService collector = mock(CollectorService.class);
        SteamPayloadProcessor processor = new SteamPayloadProcessor();
        when(fetcher.fetchGameDetails(eq(APP_ID))).thenReturn(loadFixture());

        SteamGameSource source = new SteamGameSource(fetcher, processor);
        new CrawlPipeline(collector).run(source, String.valueOf(APP_ID));

        GoldenFiles.assertMatchesGolden("steam-game-400.json",
                SaveRawCapture.from(collector).toCanonicalJson());
    }
}
