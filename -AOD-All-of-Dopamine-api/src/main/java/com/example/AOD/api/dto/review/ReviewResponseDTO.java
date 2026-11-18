package com.example.AOD.api.dto.review;

import com.example.AOD.domain.Review;
import com.example.AOD.user.model.User;
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

    /** 탈퇴한 회원의 리뷰에 보일 작성자 이름. */
    public static final String WITHDRAWN_USERNAME = "탈퇴한 사용자";

    public static ReviewResponseDTO from(Review review) {
        User author = review.getUser();   // 탈퇴한 회원의 리뷰는 null
        return ReviewResponseDTO.builder()
                .reviewId(review.getReviewId())
                .contentId(review.getContent().getContentId())
                .contentTitle(review.getContent().getMasterTitle())
                .userId(author == null ? null : author.getId())
                .username(author == null ? WITHDRAWN_USERNAME : author.getUsername())
                .rating(review.getRating())
                .title(review.getTitle())
                .content(review.getReviewContent())
                .createdAt(review.getCreatedAt())
                .updatedAt(review.getUpdatedAt())
                .build();
    }
    
    public static ReviewResponseDTO from(Review review, String currentUsername) {
        ReviewResponseDTO dto = from(review);
        dto.setIsMyReview(review.isWrittenBy(currentUsername));
        return dto;
    }
}


