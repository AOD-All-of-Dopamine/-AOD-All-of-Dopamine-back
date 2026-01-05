package com.example.crawler.ranking.common;

import com.example.shared.entity.PlatformData;
import com.example.shared.entity.ExternalRanking;
import com.example.shared.repository.ExternalRankingRepository;
import com.example.shared.repository.PlatformDataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 랭킹 데이터 Upsert 헬퍼 클래스
 * - 기존 작품: ID 유지하며 랭킹/제목 업데이트
 * - 신규 작품: 새로 추가
 * - 제외 작품: 삭제
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RankingUpsertHelper {

    private final ExternalRankingRepository rankingRepository;
    private final PlatformDataRepository platformDataRepository;

    /**
     * 랭킹 데이터 Upsert (Insert or Update)
     * 
     * @param newRankings 새로운 랭킹 데이터 목록
     * @param platform 플랫폼 이름 (e.g., "TMDB_MOVIE", "STEAM_GAME")
     */
    public void upsertRankings(List<ExternalRanking> newRankings, String platform) {
        if (newRankings == null || newRankings.isEmpty()) {
            log.warn("업데이트할 랭킹 데이터가 없습니다. platform={}", platform);
            return;
        }

        List<ExternalRanking> toSave = new ArrayList<>();
        List<String> newPlatformSpecificIds = new ArrayList<>();

        // 1. 기존 데이터 조회 및 병합
        for (ExternalRanking newRanking : newRankings) {
            newPlatformSpecificIds.add(newRanking.getPlatformSpecificId());
            
            // 내부 Content 매핑 시도 (저장 시점 매핑)
            mapToInternalContent(newRanking, platform);

            Optional<ExternalRanking> existingOpt = rankingRepository
                    .findByPlatformAndPlatformSpecificId(platform, newRanking.getPlatformSpecificId());

            if (existingOpt.isPresent()) {
                // 기존 작품: ID 유지하며 업데이트
                ExternalRanking existing = existingOpt.get();
                existing.setRanking(newRanking.getRanking());
                existing.setTitle(newRanking.getTitle());
                existing.setContent(newRanking.getContent()); // 매핑 정보 업데이트
                toSave.add(existing);
                log.debug("기존 작품 업데이트: id={}, 새 순위={}", 
                        existing.getPlatformSpecificId(), newRanking.getRanking());
            } else {
                // 신규 작품: 그대로 추가
                toSave.add(newRanking);
                log.debug("신규 작품 추가: id={}, 순위={}", 
                        newRanking.getPlatformSpecificId(), newRanking.getRanking());
            }
        }

        // 2. 저장 (기존 ID 유지됨)
        rankingRepository.saveAll(toSave);
        log.info("{} 플랫폼 랭킹 {}개 저장 완료", platform, toSave.size());

        // 3. 랭킹에서 제외된 작품 삭제
        deleteRankingsNotInList(platform, newPlatformSpecificIds);
    }

    private void mapToInternalContent(ExternalRanking ranking, String platform) {
        try {
            Optional<PlatformData> platformDataOpt = platformDataRepository
                    .findByPlatformNameAndPlatformSpecificId(platform, ranking.getPlatformSpecificId());
            
            if (platformDataOpt.isPresent()) {
                ranking.setContent(platformDataOpt.get().getContent());
                log.debug("작품 매핑 성공: {} -> contentId={}", ranking.getTitle(), platformDataOpt.get().getContent().getContentId());
            } else {
                log.debug("작품 매핑 실패 (DB에 없음): {} ({})", ranking.getTitle(), ranking.getPlatformSpecificId());
            }
        } catch (Exception e) {
            log.warn("작품 매핑 중 오류 발생: {}", e.getMessage());
        }
    }

    /**
     * 현재 랭킹 목록에 없는 작품 삭제
     * 
     * @param platform 플랫폼 이름
     * @param currentPlatformSpecificIds 현재 랭킹에 있는 ID 목록
     */
    private void deleteRankingsNotInList(String platform, List<String> currentPlatformSpecificIds) {
        List<ExternalRanking> allRankings = rankingRepository.findByPlatform(platform);
        List<ExternalRanking> toDelete = allRankings.stream()
                .filter(ranking -> !currentPlatformSpecificIds.contains(ranking.getPlatformSpecificId()))
                .toList();

        if (!toDelete.isEmpty()) {
            rankingRepository.deleteAllInBatch(toDelete);
            log.info("{} 플랫폼에서 제외된 {}개 작품 삭제", platform, toDelete.size());
        }
    }

    /**
     * 특정 플랫폼의 모든 랭킹 삭제
     * 
     * @param platform 플랫폼 이름
     */
    public void deleteAllByPlatform(String platform) {
        List<ExternalRanking> rankings = rankingRepository.findByPlatform(platform);
        if (!rankings.isEmpty()) {
            rankingRepository.deleteAllInBatch(rankings);
            log.info("{} 플랫폼의 모든 랭킹 {}개 삭제", platform, rankings.size());
        }
    }
}


