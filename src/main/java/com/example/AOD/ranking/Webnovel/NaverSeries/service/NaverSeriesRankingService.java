package com.example.AOD.ranking.Webnovel.NaverSeries.service;

import com.example.AOD.ranking.entity.ExternalRanking;
import com.example.AOD.ranking.Webnovel.NaverSeries.fetcher.NaverSeriesRankingFetcher;
import com.example.AOD.ranking.repo.ExternalRankingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 네이버 시리즈(웹소설) 랭킹 서비스
 * - 일간 TOP 100 페이지에서 상위 20개만 저장
 * - 기존 NaverSeriesCrawler의 파싱 로직 재사용
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NaverSeriesRankingService {

    private final NaverSeriesRankingFetcher fetcher;
    private final ExternalRankingRepository rankingRepository;

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

            // 각 상세 페이지에서 제목 추출 (기존 크롤러와 동일한 방식)
            for (String detailUrl : detailUrls) {
                if (rank > MAX_RANKING_SIZE) break; // Top 20만

                try {
                    // productNo 추출
                    String productNo = extractQueryParam(detailUrl, "productNo");
                    if (productNo == null || productNo.isEmpty()) {
                        log.debug("productNo를 추출할 수 없는 URL 건너뜀: {}", detailUrl);
                        continue;
                    }

                    // 상세 페이지 접근하여 제목 가져오기 (기존 크롤러 로직)
                    Document detailDoc = fetcher.fetchDetailPage(detailUrl);
                    if (detailDoc == null) {
                        log.warn("상세 페이지를 가져올 수 없음: {}", detailUrl);
                        continue;
                    }

                    // og:title 메타 태그에서 제목 추출 (기존 크롤러와 동일)
                    Element ogTitle = detailDoc.selectFirst("meta[property=og:title]");
                    String rawTitle = ogTitle != null ? ogTitle.attr("content") : null;
                    
                    // 폴백: h2 태그에서 제목 추출
                    if (rawTitle == null || rawTitle.isEmpty()) {
                        Element h2 = detailDoc.selectFirst("h2");
                        rawTitle = h2 != null ? h2.text() : null;
                    }
                    
                    // [독점], [시리즈 에디션] 같은 태그 제거
                    String title = cleanTitle(rawTitle);
                    
                    if (title == null || title.isEmpty()) {
                        log.debug("제목을 추출할 수 없는 항목 건너뜀: {}", detailUrl);
                        continue;
                    }

                    // 랭킹 데이터 생성
                    ExternalRanking ranking = new ExternalRanking();
                    ranking.setRanking(rank);
                    ranking.setTitle(title);
                    ranking.setContentId(Long.parseLong(productNo));
                    ranking.setPlatform("NAVER_SERIES");
                    rankings.add(ranking);

                    log.info("랭킹 {}위: {} (productNo={})", rank, title, productNo);
                    rank++;

                } catch (Exception e) {
                    log.warn("웹소설 랭킹 항목 파싱 중 오류 발생 (건너뜀): url={}, error={}", detailUrl, e.getMessage());
                }
            }

            if (!rankings.isEmpty()) {
                // 기존 네이버 시리즈 랭킹 삭제 후 새로 저장
                List<ExternalRanking> existingRankings = rankingRepository.findByPlatform("NAVER_SERIES");
                if (!existingRankings.isEmpty()) {
                    rankingRepository.deleteAll(existingRankings);
                    log.info("기존 네이버 시리즈 랭킹 {}개를 삭제했습니다.", existingRankings.size());
                }

                rankingRepository.saveAll(rankings);
                log.info("네이버 시리즈 랭킹 업데이트 완료. 총 {}개의 데이터를 저장했습니다.", rankings.size());
            } else {
                log.warn("저장할 유효한 랭킹 데이터가 없습니다.");
            }

        } catch (Exception e) {
            log.error("네이버 시리즈 랭킹 파싱 중 심각한 오류 발생", e);
        }
    }

    /**
     * URL에서 쿼리 파라미터 추출 (기존 크롤러 로직 재사용)
     */
    private String extractQueryParam(String url, String key) {
        if (url == null) return null;
        int idx = url.indexOf('?');
        if (idx < 0) return null;
        String qs = url.substring(idx + 1);
        for (String p : qs.split("&")) {
            String[] kv = p.split("=", 2);
            if (kv.length == 2 && kv[0].equals(key)) {
                return URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    /**
     * 제목 정리: [독점], [시리즈 에디션] 등 제거 (기존 크롤러 로직 재사용)
     */
    private String cleanTitle(String raw) {
        if (raw == null) return null;
        return raw.replaceAll("\\s*\\[[^\\]]+\\]\\s*", " ")
                  .replaceAll("\\s+", " ")
                  .trim();
    }
}
