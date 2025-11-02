package com.example.AOD.repo;

import com.example.AOD.domain.entity.GameContent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface GameContentRepository extends JpaRepository<GameContent, Long> {
    
    /**
     * Developer로 게임 작품 검색 (중복 탐지용)
     */
    @Query("SELECT gc FROM GameContent gc WHERE gc.developer = :developer")
    List<GameContent> findByDeveloper(@Param("developer") String developer);
    
    /**
     * Publisher로 게임 작품 검색 (중복 탐지용)
     */
    @Query("SELECT gc FROM GameContent gc WHERE gc.publisher = :publisher")
    List<GameContent> findByPublisher(@Param("publisher") String publisher);
}
