package com.example.AOD.repo;


import com.example.AOD.domain.entity.WebnovelContent;
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
}
