package com.example.AOD.recommendation.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "content_ratings")
@Getter
@Setter
@NoArgsConstructor
public class ContentRating {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String username;
    private String contentType;
    private Long contentId;
    private String contentTitle;
    private Integer rating;

    @Column(name = "is_liked")
    private Boolean isLiked = false;  // 기본값 설정

    @Column(name = "is_watched")
    private Boolean isWatched = false;  // 기본값 설정

    @Column(name = "is_wishlist")
    private Boolean isWishlist = false;  // 기본값 설정

    @Column(columnDefinition = "TEXT")
    private String review;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();

        // null 값 방지
        if (isLiked == null) isLiked = false;
        if (isWatched == null) isWatched = false;
        if (isWishlist == null) isWishlist = false;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();

        // null 값 방지
        if (isLiked == null) isLiked = false;
        if (isWatched == null) isWatched = false;
        if (isWishlist == null) isWishlist = false;
    }

    // 안전한 boolean 체크 메서드들 추가
    public boolean isLikedSafe() {
        return Boolean.TRUE.equals(isLiked);
    }

    public boolean isWatchedSafe() {
        return Boolean.TRUE.equals(isWatched);
    }

    public boolean isWishlistSafe() {
        return Boolean.TRUE.equals(isWishlist);
    }

    // 기존 getter 메서드들 null 안전하게 수정
    public Boolean getIsLiked() {
        return isLiked != null ? isLiked : false;
    }

    public Boolean getIsWatched() {
        return isWatched != null ? isWatched : false;
    }

    public Boolean getIsWishlist() {
        return isWishlist != null ? isWishlist : false;
    }
}