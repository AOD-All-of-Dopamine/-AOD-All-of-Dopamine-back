package com.example.crawler.ranking.Webnovel.NaverSeries.service;

import com.example.crawler.contents.Novel.NaverSeriesNovel.NaverSeriesCrawler;
import com.example.crawler.ranking.Webnovel.NaverSeries.parser.NaverSeriesDetailParser;
import com.example.crawler.ranking.common.RankingUpsertHelper;
import com.example.shared.entity.ExternalRanking;
import com.example.crawler.ranking.Webnovel.NaverSeries.fetcher.NaverSeriesRankingFetcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 네이버 시리즈(웹소설) 랭킹 서비스 (리팩토링됨)
 * - Fetcher: 랭킹 페이지 및 상세 페이지 가져오기
 * - NaverSeriesDetailParser: 랭킹용 제목 추출 파서 (ranking 전용)
 * - NaverSeriesCrawler: 유틸리티 메서드 재사용 (productNo, 제목 정리)
 * - 일간 TOP 100 페이지에서 상위 20개만 저장
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NaverSeriesRankingService {

    private final NaverSeriesRankingFetcher fetcher;
    private final NaverSeriesDetailParser detailParser; // 랭킹용 파서 (제목 추출 전용)
    private final RankingUpsertHelper rankingUpsertHelper;

    private static final int MAX_RANKING_SIZE = 20;

    @Transactional
    public void updateDailyRanking() {
        log.info("네이버 시리즈 일간 랭킹 업데이트를 시작합니다.");
        
        Document doc = fetcher.fetchDailyTop100();
        
        if (doc == null) {
            log.warn("네이버 시리즈 랭킹 페이지를 가져오지 못해 작업을 중단합니다.");
            return;
        }

        try {
            // 기존 크롤러와 동일한 로직: 먼저 productNo가 있는 링크 찾고, 없으면 폴백
            Set<String> detailUrls = new java.util.LinkedHashSet<>();
            
            // 1차 시도: productNo가 명시된 링크
            for (Element a : doc.select("a[href*='/novel/detail.series'][href*='productNo=']")) {
                String href = a.attr("href");
                if (!href.startsWith("http")) {
                    href = "https://series.naver.com" + href;
                }
                detailUrls.add(href);
            }
            
            // 2차 시도 (폴백): productNo 없이 detail.series 링크
            if (detailUrls.isEmpty()) {
                for (Element a : doc.select("a[href*='/novel/detail.series']")) {
                    String href = a.attr("href");
                    if (!href.startsWith("http")) {
                        href = "https://series.naver.com" + href;
                    }
                    detailUrls.add(href);
                }
            }
            
            if (detailUrls.isEmpty()) {
                log.error("랭킹 항목을 찾을 수 없습니다. 페이지 구조가 변경되었을 수 있습니다.");
                return;
            }

            log.info("총 {}개의 웹소설을 발견했습니다. 상위 {}개만 저장합니다.", 
                    detailUrls.size(), Math.min(detailUrls.size(), MAX_RANKING_SIZE));

            List<ExternalRanking> rankings = new ArrayList<>();
            int rank = 1;

            // 각 상세 페이지에서 제목 추출 (기존 크롤러 재사용)
            for (String detailUrl : detailUrls) {
                if (rank > MAX_RANKING_SIZE) break; // Top 20만

                try {
                    // productNo 추출 (NaverSeriesCrawler 유틸 재사용)
                    String productNo = NaverSeriesCrawler.extractQueryParam(detailUrl, "productNo");
                    if (productNo == null || productNo.isEmpty()) {
                        log.debug("productNo를 추출할 수 없는 URL 건너뜀: {}", detailUrl);
                        continue;
                    }

                    // 상세 페이지 가져오기
                    Document detailDoc = fetcher.fetchDetailPage(detailUrl);
                    if (detailDoc == null) {
                        log.warn("상세 페이지를 가져올 수 없음: {}", detailUrl);
                        continue;
                    }

                    // 제목 추출 (NaverSeriesDetailParser 재사용)
                    String title = detailParser.extractTitle(detailDoc);
                    
                    if (title == null || title.isEmpty()) {
                        log.debug("제목을 추출할 수 없는 항목 건너뜀: {}", detailUrl);
                        continue;
                    }

                    // 썸네일 URL 추출
                    String thumbnailUrl = detailParser.extractThumbnailUrl(detailDoc);

                    // 랭킹 데이터 생성
                    ExternalRanking ranking = new ExternalRanking();
                    ranking.setRanking(rank);
                    ranking.setTitle(title);
                    ranking.setPlatformSpecificId(productNo);
                    ranking.setPlatform("NaverSeries");
                    ranking.setThumbnailUrl(thumbnailUrl);
                    rankings.add(ranking);

                    log.info("랭킹 {}위: {} (productNo={})", rank, title, productNo);
                    rank++;

                } catch (Exception e) {
                    log.warn("웹소설 랭킹 항목 파싱 중 오류 발생 (건너뜀): url={}, error={}", detailUrl, e.getMessage());
                }
            }

            if (!rankings.isEmpty()) {
                // 기존 데이터와 병합하여 저장 (ID 유지) - Helper 사용
                rankingUpsertHelper.upsertRankings(rankings, "NaverSeries");
                log.info("네이버 시리즈 랭킹 업데이트 완료. 총 {}개의 데이터를 저장했습니다.", rankings.size());
            } else {
                log.warn("저장할 유효한 랭킹 데이터가 없습니다.");
            }

        } catch (Exception e) {
            log.error("네이버 시리즈 랭킹 파싱 중 심각한 오류 발생", e);
        }
    }
}


