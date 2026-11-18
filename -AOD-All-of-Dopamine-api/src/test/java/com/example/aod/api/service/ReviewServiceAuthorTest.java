package com.example.AOD.api.service;

import com.example.AOD.api.dto.review.ReviewRequest;
import com.example.AOD.domain.Review;
import com.example.AOD.recommend.log.RecEventRecorder;
import com.example.AOD.repo.ReviewRepository;
import com.example.AOD.user.repository.UserRepository;
import com.example.shared.entity.Content;
import com.example.shared.repository.ContentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** 탈퇴한 회원의 리뷰(작성자 없음)는 아무도 고치거나 지울 수 없다. */
@ExtendWith(MockitoExtension.class)
class ReviewServiceAuthorTest {

    @Mock ReviewRepository reviewRepository;
    @Mock ContentRepository contentRepository;
    @Mock UserRepository userRepository;
    @Mock RecEventRecorder recorder;
    @InjectMocks ReviewService service;

    @Test
    void updatingReviewWithoutAuthorIsRefused() {
        given(reviewRepository.findById(5L)).willReturn(Optional.of(reviewWithoutAuthor()));

        RuntimeException refused = assertThrows(RuntimeException.class,
                () -> service.updateReview(5L, "kim", ReviewRequest.builder().rating(1.0).build()));

        assertEquals("리뷰 수정 권한이 없습니다.", refused.getMessage());
        verify(reviewRepository, never()).save(any(Review.class));
    }

    @Test
    void deletingReviewWithoutAuthorIsRefused() {
        given(reviewRepository.findById(5L)).willReturn(Optional.of(reviewWithoutAuthor()));

        RuntimeException refused = assertThrows(RuntimeException.class, () -> service.deleteReview(5L, "kim"));

        assertEquals("리뷰 삭제 권한이 없습니다.", refused.getMessage());
        verify(reviewRepository, never()).delete(any(Review.class));
    }

    private static Review reviewWithoutAuthor() {
        Content content = new Content();
        content.setContentId(42L);
        Review review = new Review();
        review.setReviewId(5L);
        review.setContent(content);
        review.setRating(4.0);
        review.setUser(null);
        return review;
    }
}
