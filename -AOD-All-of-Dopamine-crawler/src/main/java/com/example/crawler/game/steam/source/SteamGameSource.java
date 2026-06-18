package com.example.crawler.game.steam.source;

import com.example.crawler.crawl.ContentSource;
import com.example.crawler.crawl.CrawlPayload;
import com.example.crawler.crawl.SourceDescriptor;
import com.example.crawler.game.steam.fetcher.SteamApiFetcher;
import com.example.crawler.game.steam.processor.SteamPayloadProcessor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Steam game ContentSource. Fetch delegates to SteamApiFetcher, parse delegates to
 * SteamPayloadProcessor — identical to the legacy SteamCrawlService.collectGameByAppId path.
 */
@Component
@RequiredArgsConstructor
public class SteamGameSource implements ContentSource<Map<String, Object>> {

    private final SteamApiFetcher steamApiFetcher;
    private final SteamPayloadProcessor payloadProcessor;

    @Override
    public SourceDescriptor descriptor() {
        return SourceDescriptor.STEAM_GAME;
    }

    @Override
    public Map<String, Object> fetchDetail(String targetId) {
        return steamApiFetcher.fetchGameDetails(Long.parseLong(targetId));
    }

    @Override
    public CrawlPayload parse(String targetId, Map<String, Object> rawDetail) {
        if (!"game".equals(rawDetail.get("type"))) {
            return null; // not a game → skip (matches legacy)
        }
        return new CrawlPayload(targetId, null, payloadProcessor.process(rawDetail));
    }
}
