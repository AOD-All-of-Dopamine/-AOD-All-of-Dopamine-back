package com.example.crawler.ranking.Webtoon.NaverWebtoon.service;

import com.example.crawler.contents.Webtoon.NaverWebtoon.MobileListParser;
import com.example.crawler.contents.Webtoon.NaverWebtoon.NaverWebtoonDTO;
import com.example.crawler.ranking.Webtoon.NaverWebtoon.fetcher.NaverWebtoonRankingFetcher;
import com.example.crawler.ranking.common.RankingUpsertHelper;
import com.example.shared.entity.ExternalRanking;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 네이버 웹툰 랭킹 서비스 (리팩토링됨)
 * - Fetcher: 오늘 요일 목록 페이지 가져오기 (Jsoup)
 * - MobileListParser: 기존 크롤러의 파서 재사용 (contents 코드)
 * - 오늘 요일의 웹툰 목록에서 상위 20개만 저장
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NaverWebtoonRankingService {

    private final NaverWebtoonRankingFetcher fetcher; // Document 가져오기
    private final MobileListParser mobileListParser; // 기존 파서 재사용
    private final RankingUpsertHelper rankingUpsertHelper;

    private static final int MAX_RANKING_SIZE = 20;

    @Transactional
    public void updateTodayWebtoonRanking() {
        String todayWeekday = fetcher.getTodayWeekdayString();
        log.info("네이버 웹툰 오늘 요일({}) 랭킹 업데이트를 시작합니다.", todayWeekday);
        
        try {
            // Fetcher로 오늘 요일 페이지 가져오기 (Jsoup)
            Document doc = fetcher.fetchTodayWebtoons();
            
            if (doc == null) {
                log.error("네이버 웹툰 랭킹 페이지를 가져오지 못했습니다.");
                return;
            }
            
            // 기존 크롤러의 MobileListParser 재사용
            Map<String, NaverWebtoonDTO> webtoonsMap = mobileListParser.extractWebtoonsWithBasicInfo(
                    doc, "ranking_" + todayWeekday, todayWeekday);
            
            // Map을 List로 변환 (순서 보장)
            List<NaverWebtoonDTO> webtoonList = new ArrayList<>(webtoonsMap.values());
            
            if (webtoonList.isEmpty()) {
                log.warn("웹툰 목록을 가져오지 못해 작업을 중단합니다.");
                return;
            }

            log.info("총 {}개의 웹툰을 발견했습니다. 상위 {}개만 저장합니다.", 
                    webtoonList.size(), Math.min(webtoonList.size(), MAX_RANKING_SIZE));

            List<ExternalRanking> rankings = new ArrayList<>();
            int rank = 1;

            for (NaverWebtoonDTO dto : webtoonList) {
                if (rank > MAX_RANKING_SIZE) break; // Top 20만

                try {
                    // 랭킹 데이터 생성
                    ExternalRanking ranking = new ExternalRanking();
                    ranking.setRanking(rank);
                    ranking.setTitle(dto.getTitle());
                    ranking.setPlatformSpecificId(dto.getTitleId());
                    ranking.setPlatform("NaverWebtoon");
                    ranking.setThumbnailUrl(dto.getImageUrl());
                    rankings.add(ranking);

                    rank++;

                } catch (Exception e) {
                    log.warn("웹툰 랭킹 항목 변환 중 오류 발생 (건너뜀): {}", e.getMessage());
                }
            }

            if (!rankings.isEmpty()) {
                // 기존 데이터와 병합하여 저장 (ID 유지) - Helper 사용
                rankingUpsertHelper.upsertRankings(rankings, "NaverWebtoon");
                log.info("네이버 웹툰 랭킹 업데이트 완료. 총 {}개의 데이터를 저장했습니다. ({})요일", 
                        rankings.size(), todayWeekday);
            } else {
                log.warn("저장할 유효한 랭킹 데이터가 없습니다.");
            }

        } catch (Exception e) {
            log.error("네이버 웹툰 랭킹 업데이트 중 오류 발생", e);
        }
    }
}


