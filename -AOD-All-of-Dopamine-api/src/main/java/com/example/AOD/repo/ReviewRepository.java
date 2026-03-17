package com.example.AOD.repo;

import com.example.AOD.domain.Review;
import com.example.shared.entity.Content;
import com.example.AOD.user.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {
    
    // 특정 작품의 리뷰 목록 조회
    Page<Review> findByContent(Content content, Pageable pageable);
    
    // 특정 작품의 리뷰 목록 조회 (content_id로)
    @Query("SELECT r FROM Review r WHERE r.content.contentId = :contentId")
    Page<Review> findByContentId(@Param("contentId") Long contentId, Pageable pageable);
    
    // 특정 사용자의 리뷰 목록 조회
    Page<Review> findByUser(User user, Pageable pageable);
    
    // 특정 사용자의 특정 작품에 대한 리뷰 조회
    Optional<Review> findByContentAndUser(Content content, User user);
    
    // 특정 작품의 리뷰 존재 여부
    boolean existsByContentAndUser(Content content, User user);
    
    // [✨ 최적화: 데이터베이스 내장 함수 (AVG, COUNT) 활용]
    // 자바 메모리(WAS)로 데이터를 긁어와서 루프를 돌며 계산하지 않고,
    // 데이터베이스 엔진(DBMS)에서 가장 빠르게 집계 연산을 수행한 결과 스칼라 값만 받아옵니다.
    
    // 특정 작품의 평균 평점 계산
    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.content.contentId = :contentId")
    Double getAverageRatingByContentId(@Param("contentId") Long contentId);
    
    // 특정 작품의 리뷰 총 개수
    @Query("SELECT COUNT(r) FROM Review r WHERE r.content.contentId = :contentId")
    Long countByContentId(@Param("contentId") Long contentId);
    
    // 특정 작품의 리뷰 개수
    long countByContent(Content content);
}


