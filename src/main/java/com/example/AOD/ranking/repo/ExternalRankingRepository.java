package com.example.AOD.ranking.repo;

import com.example.AOD.ranking.entity.ExternalRanking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ExternalRankingRepository extends JpaRepository<ExternalRanking, Long> {
    List<ExternalRanking> findByPlatform(String platform);
    
    Optional<ExternalRanking> findByPlatformAndContentId(String platform, Long contentId);
}
