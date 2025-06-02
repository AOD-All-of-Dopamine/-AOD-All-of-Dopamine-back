package com.example.AOD.common.repository;

import com.example.AOD.common.commonDomain.GamePlatformMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GamePlatformMappingRepository extends JpaRepository<GamePlatformMapping, Long> {

    Optional<GamePlatformMapping> findBySteamId(Long steamId);
    Optional<GamePlatformMapping> findByEpicId(Long epicId);
    Optional<GamePlatformMapping> findByGogId(Long gogId);

    @Query("SELECT gpm FROM GamePlatformMapping gpm WHERE gpm.steamId > 0")
    List<GamePlatformMapping> findBySteamAvailable();

    @Query("SELECT gpm FROM GamePlatformMapping gpm WHERE gpm.steamId > 0 AND gpm.epicId IS NULL AND gpm.gogId IS NULL")
    List<GamePlatformMapping> findSteamExclusives();
}