package com.example.AOD.commonV2.repository;

import com.example.AOD.commonV2.domain.GamePlatformMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GamePlatformMappingRepository extends JpaRepository<GamePlatformMapping, Long> {

    // 스팀 ID로 검색
    Optional<GamePlatformMapping> findBySteamId(Long steamId);

    // 에픽 ID로 검색
    Optional<GamePlatformMapping> findByEpicId(Long epicId);

    // GOG ID로 검색
    Optional<GamePlatformMapping> findByGogId(Long gogId);

    // 특정 플랫폼에만 있는 게임들
    @Query("SELECT gpm FROM GamePlatformMapping gpm WHERE gpm.steamId > 0 AND gpm.epicId IS NULL AND gpm.gogId IS NULL")
    List<GamePlatformMapping> findSteamExclusives();

    // 멀티플랫폼 게임들 (2개 이상 플랫폼에 존재)
    @Query("SELECT gpm FROM GamePlatformMapping gpm WHERE " +
            "(CASE WHEN gpm.steamId > 0 THEN 1 ELSE 0 END) + " +
            "(CASE WHEN gpm.epicId > 0 THEN 1 ELSE 0 END) + " +
            "(CASE WHEN gpm.gogId > 0 THEN 1 ELSE 0 END) >= 2")
    List<GamePlatformMapping> findMultiPlatformGames();
}