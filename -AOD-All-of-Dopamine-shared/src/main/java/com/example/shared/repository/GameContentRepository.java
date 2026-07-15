package com.example.shared.repository;

import com.example.shared.entity.GameContent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
    
    /**
     * Content ID 목록으로 조회
     */
    List<GameContent> findByContentIdIn(List<Long> contentIds);

}
