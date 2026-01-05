package com.example.AOD.api.dto.review;

import com.example.AOD.domain.Review;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewResponseDTO {
    private Long reviewId;
    private Long contentId;
    private String contentTitle;
    private Long userId;
    private String username;
    private Double rating;
    private String title;
    private String content;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Boolean isMyReview; // 현재 사용자의 리뷰인지 여부

    public static ReviewResponseDTO from(Review review) {
        return ReviewResponseDTO.builder()
                .reviewId(review.getReviewId())
                .contentId(review.getContent().getContentId())
                .contentTitle(review.getContent().getMasterTitle())
                .userId(review.getUser().getId())
                .username(review.getUser().getUsername())
                .rating(review.getRating())
                .title(review.getTitle())
                .content(review.getReviewContent())
                .createdAt(review.getCreatedAt())
                .updatedAt(review.getUpdatedAt())
                .build();
    }
    
    public static ReviewResponseDTO from(Review review, String currentUsername) {
        ReviewResponseDTO dto = from(review);
        dto.setIsMyReview(review.getUser().getUsername().equals(currentUsername));
        return dto;
    }
}


