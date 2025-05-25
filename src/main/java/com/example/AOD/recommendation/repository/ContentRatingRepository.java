package com.example.AOD.recommendation.repository;

import com.example.AOD.recommendation.domain.ContentRating;
import com.example.AOD.recommendation.domain.UserPreference;
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
}