package com.example.AOD.api.dto.review;

import com.example.AOD.domain.Review;
import com.example.AOD.user.model.User;
import com.example.shared.entity.Content;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 탈퇴한 회원의 리뷰는 작성자 없이 남는다 — 그 리뷰가 응답에서 어떻게 보이는가. */
class ReviewResponseDTOTest {

    @Test
    void reviewWithoutAuthorIsShownAsWithdrawnUser() {
        Review review = reviewOn(content(42L));
        review.setUser(null);

        ReviewResponseDTO dto = ReviewResponseDTO.from(review);

        assertEquals("탈퇴한 사용자", dto.getUsername());
        assertNull(dto.getUserId());
    }

    @Test
    void reviewWithoutAuthorIsNobodysOwnReview() {
        Review review = reviewOn(content(42L));
        review.setUser(null);

        ReviewResponseDTO dto = ReviewResponseDTO.from(review, "kim");

        assertFalse(dto.getIsMyReview());
    }

    @Test
    void reviewIsMineWhenIWroteIt() {
        Review review = reviewOn(content(42L));
        review.setUser(user(7L, "kim"));

        ReviewResponseDTO dto = ReviewResponseDTO.from(review, "kim");

        assertTrue(dto.getIsMyReview());
        assertEquals("kim", dto.getUsername());
        assertEquals(7L, dto.getUserId());
    }

    private static Review reviewOn(Content content) {
        Review review = new Review();
        review.setReviewId(5L);
        review.setContent(content);
        review.setRating(4.0);
        return review;
    }

    private static Content content(Long id) {
        Content content = new Content();
        content.setContentId(id);
        content.setMasterTitle("작품");
        return content;
    }

    private static User user(Long id, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        return user;
    }
}
