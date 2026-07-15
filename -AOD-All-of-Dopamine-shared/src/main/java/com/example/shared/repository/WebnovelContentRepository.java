package com.example.shared.repository;

import com.example.shared.entity.WebnovelContent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface WebnovelContentRepository extends JpaRepository<WebnovelContent, Long> {
    
    /**
     * Author로 웹소설 작품 검색 (중복 탐지용)
     */
    @Query("SELECT wn FROM WebnovelContent wn WHERE wn.author = :author")
    List<WebnovelContent> findByAuthor(@Param("author") String author);
    
    /**
     * Content ID 목록으로 조회
     */
    List<WebnovelContent> findByContentIdIn(List<Long> contentIds);

}
