package com.example.AOD.ranking.service;

import com.example.AOD.ranking.entity.ExternalRanking;
import com.example.AOD.ranking.repo.ExternalRankingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RankingService {

    private final ExternalRankingRepository rankingRepository;

    @Transactional
    public void saveRankings(List<ExternalRanking> rankings) {
        // 기존 랭킹 정보를 지우고 새로 저장하거나, 플랫폼별로 업데이트하는 로직 추가 가능
        rankingRepository.saveAll(rankings);
    }

    @Transactional(readOnly = true)
    public List<ExternalRanking> getRankingsByPlatform(String platform) {
        // 이 부분은 추후 구체적인 쿼리 메소드로 변경될 수 있습니다.
        // 예: return rankingRepository.findByPlatformOrderByRankingAsc(platform);
        return rankingRepository.findAll().stream()
                .filter(r -> platform.equals(r.getPlatform()))
                .toList();
    }
}
