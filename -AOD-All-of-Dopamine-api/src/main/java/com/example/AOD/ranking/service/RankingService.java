package com.example.AOD.ranking.service;

import com.example.shared.entity.ExternalRanking;
import com.example.shared.repository.ExternalRankingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 랭킹 조회 전용 서비스 (API 서버)
 * - 크롤링은 크롤러 서버에서 담당
 * - API 서버는 조회만 제공
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RankingService {

    private final ExternalRankingRepository rankingRepository;

    /**
     * 플랫폼별 랭킹 조회
     */
    @Transactional(readOnly = true)
    public List<ExternalRanking> getRankingsByPlatform(String platform) {
        return rankingRepository.findByPlatformWithContent(platform);
    }

    /**
     * 전체 랭킹 조회
     */
    @Transactional(readOnly = true)
    public List<ExternalRanking> getAllRankings() {
        return rankingRepository.findAllWithContent();
    }

    /**
     * 도메인별 랭킹 조회 (예: MOVIE, TV, GAME 등)
     */
    @Transactional(readOnly = true)
    public List<ExternalRanking> getRankingsByDomain(String domain) {
        return rankingRepository.findAll().stream()
            .filter(r -> r.getContent() != null && 
                        r.getContent().getDomain().name().equals(domain))
            .toList();
    }
}
