package com.example.shared.repository;

import com.example.shared.entity.TvContent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TvContentRepository extends JpaRepository<TvContent, Long> {
    
    /**
     * Content ID 목록으로 조회
     */
    List<TvContent> findByContentIdIn(List<Long> contentIds);

}
