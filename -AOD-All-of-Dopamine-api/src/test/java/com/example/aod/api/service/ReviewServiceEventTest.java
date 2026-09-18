package com.example.AOD.api.service;

import com.example.AOD.api.dto.review.ReviewRequest;
import com.example.AOD.domain.Review;
import com.example.AOD.recommend.context.RecContext;
import com.example.AOD.recommend.log.RecEventRecorder;
import com.example.AOD.repo.ReviewRepository;
import com.example.AOD.user.model.User;
import com.example.AOD.user.repository.UserRepository;
import com.example.shared.entity.Content;
import com.example.shared.repository.ContentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ReviewServiceEventTest {

    @Mock ReviewRepository reviewRepository;
    @Mock ContentRepository contentRepository;
    @Mock UserRepository userRepository;
    @Mock RecEventRecorder recorder;
    @InjectMocks ReviewService service;

    private Content content;
    private User user;

    @BeforeEach
    void setUp() {
        content = new Content();
        content.setContentId(42L);
        content.setMasterTitle("테스트 작품");
        user = new User();
        user.setId(7L);
        user.setUsername("tester");
    }

    @Test
    void createReviewLogsReviewSavedAsNew() {
        given(contentRepository.findById(42L)).willReturn(Optional.of(content));
        given(userRepository.findByUsername("tester")).willReturn(Optional.of(user));
        given(reviewRepository.existsByContentAndUser(content, user)).willReturn(false);
        given(reviewRepository.save(any(Review.class))).willAnswer(inv -> inv.getArgument(0));

        service.createReview(42L, "tester", ReviewRequest.builder().rating(4.5).title("좋다").content("본문").build());

        verify(recorder).reviewSaved(eq(7L), eq(42L), eq(4.5), eq(true), any(RecContext.class));
    }

    @Test
    void updateReviewLogsReviewSavedAsEdit() {
        Review review = new Review();
        review.setReviewId(5L);
        review.setContent(content);
        review.setUser(user);
        review.setRating(3.0);
        given(reviewRepository.findById(5L)).willReturn(Optional.of(review));
        given(reviewRepository.save(any(Review.class))).willAnswer(inv -> inv.getArgument(0));

        service.updateReview(5L, "tester", ReviewRequest.builder().rating(2.0).build());

        verify(recorder).reviewSaved(eq(7L), eq(42L), eq(2.0), eq(false), any(RecContext.class));
    }
}
