package com.example.AOD.common.repository;

import com.example.AOD.common.commonDomain.OTTPlatformMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OTTPlatformMappingRepository extends JpaRepository<OTTPlatformMapping, Long> {

    Optional<OTTPlatformMapping> findByNetflixId(Long netflixId);
    Optional<OTTPlatformMapping> findByDisneyPlusId(Long disneyPlusId);
    Optional<OTTPlatformMapping> findByWatchaId(Long watchaId);
    Optional<OTTPlatformMapping> findByWavveId(Long wavveId);

    @Query("SELECT opm FROM OTTPlatformMapping opm WHERE opm.netflixId > 0")
    List<OTTPlatformMapping> findByNetflixAvailable();

    @Query("SELECT opm FROM OTTPlatformMapping opm WHERE opm.netflixId > 0 AND opm.disneyPlusId IS NULL AND opm.watchaId IS NULL AND opm.wavveId IS NULL")
    List<OTTPlatformMapping> findNetflixExclusives();
}