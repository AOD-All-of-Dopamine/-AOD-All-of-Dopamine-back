package com.example.crawler.contents.Novel.NaverSeriesNovel;

import com.example.crawler.crawl.CrawlPipeline;
import com.example.crawler.crawl.support.GoldenFiles;
import com.example.crawler.crawl.support.SaveRawCapture;
import com.example.crawler.ingest.CollectorService;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NaverSeriesNovelGoldenTest {

    private static final String PRODUCT_ID = "12345";
    private static final String FIRST_DATE = "2020-01-01";

    private Document fixtureDoc() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/fixtures/naverseries/novel-12345.html")) {
            Objects.requireNonNull(in, "fixture not found: /fixtures/naverseries/novel-12345.html");
            String html = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return Jsoup.parse(html, "https://series.naver.com/");
        }
    }

    @Test
    void naverSeriesNovelSourceMatchesGolden() throws Exception {
        CollectorService collector = mock(CollectorService.class);
        NaverSeriesNovelFetcher fetcher = mock(NaverSeriesNovelFetcher.class);
        when(fetcher.fetchDetail(PRODUCT_ID)).thenReturn(new NaverSeriesDetail(fixtureDoc(), FIRST_DATE));

        NaverSeriesNovelSource source = new NaverSeriesNovelSource(fetcher, new NaverSeriesNovelParser());
        new CrawlPipeline(collector).run(source, PRODUCT_ID);

        GoldenFiles.assertMatchesGolden("naverseries-novel-12345.json",
                SaveRawCapture.from(collector).toCanonicalJson());
    }
}
