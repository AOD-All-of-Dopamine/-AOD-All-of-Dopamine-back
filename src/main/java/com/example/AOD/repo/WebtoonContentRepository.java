package com.example.AOD.repo;

import com.example.AOD.domain.entity.WebtoonContent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface WebtoonContentRepository extends JpaRepository<WebtoonContent, Long> {
    
    /**
     * Author로 웹툰 작품 검색 (중복 탐지용)
     */
    @Query("SELECT wc FROM WebtoonContent wc WHERE wc.author = :author")
    List<WebtoonContent> findByAuthor(@Param("author") String author);
}