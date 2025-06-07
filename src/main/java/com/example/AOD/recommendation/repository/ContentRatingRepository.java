package com.example.AOD.recommendation.repository;

import com.example.AOD.recommendation.domain.ContentRating;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ContentRatingRepository extends JpaRepository<ContentRating, Long> {
    List<ContentRating> findByUsername(String username);
    List<ContentRating> findByUsernameAndContentType(String username, String contentType);
    List<ContentRating> findByUsernameAndIsLikedTrue(String username);
    List<ContentRating> findByUsernameAndIsWishlistTrue(String username);
    Optional<ContentRating> findByUsernameAndContentTypeAndContentId(String username, String contentType, Long contentId);

    // 평점 평균 계산용
    @Query("SELECT AVG(r.rating) FROM ContentRating r WHERE r.contentType = :contentType AND r.contentId = :contentId")
    Double getAverageRatingByContentTypeAndId(@Param("contentType") String contentType, @Param("contentId") Long contentId);

    // 협업 필터링을 위한 추가 메서드
    @Query("SELECT cr FROM ContentRating cr WHERE cr.contentType = :contentType")
    List<ContentRating> findByContentType(@Param("contentType") String contentType);

    // 사용자가 평가한 모든 콘텐츠 ID 조회
    @Query("SELECT DISTINCT cr.contentId FROM ContentRating cr WHERE cr.username = :username")
    List<Long> findRatedContentIdsByUsername(@Param("username") String username);

    // 특정 콘텐츠 타입에서 사용자가 평가한 콘텐츠 ID 조회
    @Query("SELECT DISTINCT cr.contentId FROM ContentRating cr WHERE cr.username = :username AND cr.contentType = :contentType")
    List<Long> findRatedContentIdsByUsernameAndContentType(@Param("username") String username, @Param("contentType") String contentType);
}