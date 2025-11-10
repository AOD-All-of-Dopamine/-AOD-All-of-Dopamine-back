package com.example.AOD.ranking.Webtoon.NaverWebtoon.service;

import com.example.AOD.contents.Webtoon.NaverWebtoon.MobileListParser;
import com.example.AOD.contents.Webtoon.NaverWebtoon.NaverWebtoonDTO;
import com.example.AOD.ranking.entity.ExternalRanking;
import com.example.AOD.ranking.Webtoon.NaverWebtoon.fetcher.NaverWebtoonRankingFetcher;
import com.example.AOD.ranking.repo.ExternalRankingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 네이버 웹툰 랭킹 서비스
 * - 오늘 요일의 웹툰 목록에서 상위 20개만 저장
 * - 네이버 웹툰은 이미 인기순으로 정렬되어 있음
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NaverWebtoonRankingService {

    private final NaverWebtoonRankingFetcher fetcher;
    private final ExternalRankingRepository rankingRepository;
    private final MobileListParser mobileListParser; // 기존 파서 재사용

    private static final int MAX_RANKING_SIZE = 20;

    @Transactional
    public void updateTodayWebtoonRanking() {
        log.info("네이버 웹툰 오늘 요일 랭킹 업데이트를 시작합니다.");
        
        Document doc = fetcher.fetchTodayWebtoons();
        
        if (doc == null) {
            log.warn("네이버 웹툰 랭킹 페이지를 가져오지 못해 작업을 중단합니다.");
            return;
        }

        try {
            // 기존 MobileListParser를 사용하여 웹툰 목록 추출
            String todayWeekday = fetcher.getTodayWeekdayString();
            Map<String, NaverWebtoonDTO> webtoonsMap = mobileListParser.extractWebtoonsWithBasicInfo(
                    doc, "ranking_" + todayWeekday, todayWeekday);
            
            if (webtoonsMap.isEmpty()) {
                log.error("웹툰 목록을 찾을 수 없습니다. 페이지 구조가 변경되었을 수 있습니다.");
                return;
            }

            log.info("총 {}개의 웹툰을 발견했습니다. 상위 {}개만 저장합니다.", 
                    webtoonsMap.size(), Math.min(webtoonsMap.size(), MAX_RANKING_SIZE));

            List<ExternalRanking> rankings = new ArrayList<>();
            int rank = 1;

            // Map은 순서가 보장되지 않을 수 있으므로, List로 변환
            List<NaverWebtoonDTO> webtoonList = new ArrayList<>(webtoonsMap.values());

            for (NaverWebtoonDTO dto : webtoonList) {
                if (rank > MAX_RANKING_SIZE) break; // Top 20만

                try {
                    // 랭킹 데이터 생성
                    ExternalRanking ranking = new ExternalRanking();
                    ranking.setRanking(rank);
                    ranking.setTitle(dto.getTitle());
                    ranking.setContentId(Long.parseLong(dto.getTitleId()));
                    ranking.setPlatform("NAVER_WEBTOON");
                    rankings.add(ranking);

                    rank++;

                } catch (Exception e) {
                    log.warn("웹툰 랭킹 항목 변환 중 오류 발생 (건너뜀): {}", e.getMessage());
                }
            }

            if (!rankings.isEmpty()) {
                // 기존 네이버 웹툰 랭킹 삭제 후 새로 저장
                List<ExternalRanking> existingRankings = rankingRepository.findByPlatform("NAVER_WEBTOON");
                if (!existingRankings.isEmpty()) {
                    rankingRepository.deleteAll(existingRankings);
                    log.info("기존 네이버 웹툰 랭킹 {}개를 삭제했습니다.", existingRankings.size());
                }

                rankingRepository.saveAll(rankings);
                log.info("네이버 웹툰 랭킹 업데이트 완료. 총 {}개의 데이터를 저장했습니다.", rankings.size());
            } else {
                log.warn("저장할 유효한 랭킹 데이터가 없습니다.");
            }

        } catch (Exception e) {
            log.error("네이버 웹툰 랭킹 파싱 중 심각한 오류 발생", e);
        }
    }
}
