package com.example.crawler.contents.Novel.NaverSeriesNovel;

import com.example.crawler.crawl.CrawlPayload;
import com.example.crawler.crawl.SourceDescriptor;
import com.example.crawler.ingest.CollectorService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Naver Series(웹소설) 관리자용 목록 크롤러
 * - 목록 페이지에서 상세 링크(productNo)만 수집
 * - 각 상세는 공유 Fetcher + Parser로 위임 (큐 경로와 동일한 파싱 로직 재사용)
 */
@Slf4j
@Component
public class NaverSeriesCrawler {

    private final CollectorService collector;
    private final NaverSeriesNovelFetcher fetcher;
    private final NaverSeriesNovelParser parser;

    public NaverSeriesCrawler(CollectorService collector,
                              NaverSeriesNovelFetcher fetcher,
                              NaverSeriesNovelParser parser) {
        this.collector = collector;
        this.fetcher = fetcher;
        this.parser = parser;
    }

    public int crawlToRaw(String baseListUrl, String cookieString, int maxPages) throws Exception {
        int saved = 0;
        int page = 1;

        while (true) {
            if (Thread.currentThread().isInterrupted()) {
                log.info("작업 인터럽트 감지, 크롤링 중단 (현재까지 {}개 저장)", saved);
                return saved;
            }
            if (maxPages > 0 && page > maxPages) break;

            org.jsoup.nodes.Document listDoc = fetcher.getByUrl(baseListUrl + page, cookieString);

            java.util.Set<String> productNos = new java.util.LinkedHashSet<>();
            for (org.jsoup.nodes.Element a : listDoc.select("a[href*='/novel/detail.series'][href*='productNo=']")) {
                String pn = NaverSeriesNovelParser.extractQueryParam(absUrl(a.attr("href")), "productNo");
                if (pn != null) productNos.add(pn);
            }
            if (productNos.isEmpty()) {
                for (org.jsoup.nodes.Element a : listDoc.select("a[href*='/novel/detail.series']")) {
                    String pn = NaverSeriesNovelParser.extractQueryParam(absUrl(a.attr("href")), "productNo");
                    if (pn != null) productNos.add(pn);
                }
            }
            if (productNos.isEmpty()) break;

            for (String productNo : productNos) {
                NaverSeriesDetail detail = fetcher.fetchDetail(productNo, cookieString);
                CrawlPayload cp = parser.parse(productNo, detail);
                if (cp == null) continue; // adult / no title
                collector.saveRaw(SourceDescriptor.NAVER_SERIES.platformName(), SourceDescriptor.NAVER_SERIES.domain(),
                        cp.payload(), cp.platformSpecificId(), cp.url());
                saved++;
            }
            page++;
        }
        return saved;
    }

    private static String absUrl(String href) {
        if (href == null) return null;
        return href.startsWith("http") ? href : "https://series.naver.com" + href;
    }
}
