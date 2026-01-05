package com.example.shared.repository;

import com.example.shared.entity.ExternalRanking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ExternalRankingRepository extends JpaRepository<ExternalRanking, Long> {
    
    List<ExternalRanking> findByPlatform(String platform);
    
    Optional<ExternalRanking> findByPlatformAndPlatformSpecificId(String platform, String platformSpecificId);
    
    @Query("SELECT er FROM ExternalRanking er WHERE er.platform = :platform ORDER BY er.ranking ASC")
    List<ExternalRanking> findByPlatformOrdered(@Param("platform") String platform);
}
