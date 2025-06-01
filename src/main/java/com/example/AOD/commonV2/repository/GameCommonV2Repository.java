package com.example.AOD.commonV2.repository;

import com.example.AOD.commonV2.domain.GameCommonV2;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GameCommonV2Repository extends JpaRepository<GameCommonV2, Long> {

    // 제목으로 검색
    List<GameCommonV2> findByTitleContainingIgnoreCase(String title);

    // 정확한 제목 매칭
    Optional<GameCommonV2> findByTitleIgnoreCase(String title);

    // 개발사로 검색
    @Query("SELECT g FROM GameCommonV2 g WHERE :developer MEMBER OF g.developers")
    List<GameCommonV2> findByDeveloper(@Param("developer") String developer);

    // 퍼블리셔로 검색
    @Query("SELECT g FROM GameCommonV2 g WHERE :publisher MEMBER OF g.publishers")
    List<GameCommonV2> findByPublisher(@Param("publisher") String publisher);

    // 장르로 검색
    @Query("SELECT g FROM GameCommonV2 g WHERE :genre MEMBER OF g.genres")
    List<GameCommonV2> findByGenre(@Param("genre") String genre);

    // 연령 제한 이하 검색
    List<GameCommonV2> findByRequiredAgeLessThanEqual(Long requiredAge);

    // 가격 범위 검색
    List<GameCommonV2> findByFinalPriceBetween(Integer minPrice, Integer maxPrice);

    // 무료 게임 검색
    List<GameCommonV2> findByFinalPrice(Integer price);

    // 스팀에 있는 게임들 검색
    @Query("SELECT g FROM GameCommonV2 g WHERE g.platformMapping.steamId > 0")
    List<GameCommonV2> findBySteamAvailable();

    // 에픽게임즈에 있는 게임들 검색
    @Query("SELECT g FROM GameCommonV2 g WHERE g.platformMapping.epicId > 0")
    List<GameCommonV2> findByEpicAvailable();

    // 특정 플랫폼 ID로 검색
    @Query("SELECT g FROM GameCommonV2 g WHERE g.platformMapping.steamId = :steamId")
    Optional<GameCommonV2> findBySteamId(@Param("steamId") Long steamId);

    @Query("SELECT g FROM GameCommonV2 g WHERE g.platformMapping.epicId = :epicId")
    Optional<GameCommonV2> findByEpicId(@Param("epicId") Long epicId);

    // 페이징된 제목 검색
    Page<GameCommonV2> findByTitleContainingIgnoreCase(String title, Pageable pageable);
}