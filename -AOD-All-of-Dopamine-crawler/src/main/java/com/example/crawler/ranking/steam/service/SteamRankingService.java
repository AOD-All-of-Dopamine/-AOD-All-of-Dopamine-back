package com.example.crawler.ranking.steam.service;

import com.example.crawler.ranking.common.RankingUpsertHelper;
import com.example.shared.entity.ExternalRanking;
import com.example.crawler.ranking.steam.fetcher.SteamRankingFetcher;
import com.example.crawler.ranking.steam.parser.SteamRankingParser.SteamGameData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Steam 랭킹 서비스 (Selenium 방식으로 한국 Top Sellers 크롤링)
 * - Headless Chrome으로 서버 환경(리눅스)에서도 동작
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SteamRankingService {

    private final SteamRankingFetcher steamRankingFetcher;
    private final RankingUpsertHelper rankingUpsertHelper;

    private static final String PLATFORM_NAME = "Steam";

    @Transactional
    public void updateTopSellersRanking() {
        log.info("Steam 최고 판매 랭킹 업데이트를 시작합니다 (Selenium 방식).");

        // 1. Selenium으로 페이지에서 게임 데이터 크롤링
        List<SteamGameData> gameDataList = steamRankingFetcher.fetchTopSellers();

        if (gameDataList.isEmpty()) {
            log.warn("Steam 페이지에서 게임 데이터를 가져오지 못했습니다.");
            return;
        }

        // 2. 엔티티 변환
        List<ExternalRanking> rankings = convertToRankings(gameDataList);

        if (rankings.isEmpty()) {
            log.warn("변환된 Steam 랭킹 데이터가 없습니다.");
            return;
        }

        // 3. 기존 데이터와 병합하여 저장 (ID 유지) - Helper 사용
        rankingUpsertHelper.upsertRankings(rankings, PLATFORM_NAME);

        log.info("Steam 최고 판매 랭킹 업데이트 완료. 총 {}개", rankings.size());
    }

    /**
     * SteamGameData를 ExternalRanking으로 변환
     */
    private List<ExternalRanking> convertToRankings(List<SteamGameData> gameDataList) {
        List<ExternalRanking> rankings = new ArrayList<>();

        for (SteamGameData gameData : gameDataList) {
            try {
                ExternalRanking ranking = new ExternalRanking();
                ranking.setPlatformSpecificId(String.valueOf(gameData.getAppId()));
                ranking.setTitle(gameData.getTitle());
                ranking.setRanking(gameData.getRank());
                ranking.setPlatform(PLATFORM_NAME);
                ranking.setThumbnailUrl(gameData.getThumbnailUrl());
                rankings.add(ranking);
            } catch (Exception e) {
                log.warn("Steam 랭킹 항목 변환 중 오류 발생 (건너뜀): rank={}, error={}", 
                        gameData.getRank(), e.getMessage());
            }
        }

        return rankings;
    }
}


