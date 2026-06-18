package com.example.crawler.contents.Novel.NaverSeriesNovel;

import com.example.crawler.crawl.ContentSource;
import com.example.crawler.crawl.CrawlPayload;
import com.example.crawler.crawl.SourceDescriptor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** NaverSeries webnovel ContentSource. Fetch via Jsoup (no cookie, like legacy collectNovelById), parse pure. */
@Slf4j
@Component
@RequiredArgsConstructor
public class NaverSeriesNovelSource implements ContentSource<NaverSeriesDetail> {

    private final NaverSeriesNovelFetcher fetcher;
    private final NaverSeriesNovelParser parser;

    @Override
    public SourceDescriptor descriptor() {
        return SourceDescriptor.NAVER_SERIES;
    }

    @Override
    public NaverSeriesDetail fetchDetail(String targetId) {
        try {
            return fetcher.fetchDetail(targetId);
        } catch (Exception e) {
            throw new RuntimeException("NaverSeries fetch failed for " + targetId, e);
        }
    }

    @Override
    public CrawlPayload parse(String targetId, NaverSeriesDetail rawDetail) {
        return parser.parse(targetId, rawDetail);
    }
}
