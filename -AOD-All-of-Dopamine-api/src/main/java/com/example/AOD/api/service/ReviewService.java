package com.example.AOD.api.service;

import com.example.AOD.api.dto.PageResponse;
import com.example.AOD.api.dto.review.ReviewRequest;
import com.example.AOD.api.dto.review.ReviewResponseDTO;
import com.example.shared.entity.Content;
import com.example.AOD.domain.Review;
import com.example.shared.repository.ContentRepository;
import com.example.AOD.repo.ReviewRepository;
import com.example.AOD.user.model.User;
import com.example.AOD.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final ContentRepository contentRepository;
    private final UserRepository userRepository;

    /**
     * 특정 작품의 리뷰 목록 조회
     */
    public PageResponse<ReviewResponseDTO> getReviewsByContentId(Long contentId, String currentUsername, Pageable pageable) {
        Page<Review> reviewPage = reviewRepository.findByContentId(contentId, pageable);

        List<ReviewResponseDTO> content = reviewPage.getContent().stream()
                .map(review -> currentUsername != null 
                        ? ReviewResponseDTO.from(review, currentUsername)
                        : ReviewResponseDTO.from(review))
                .collect(Collectors.toList());

        return PageResponse.<ReviewResponseDTO>builder()
                .content(content)
                .page(reviewPage.getNumber())
                .size(reviewPage.getSize())
                .totalElements(reviewPage.getTotalElements())
                .totalPages(reviewPage.getTotalPages())
                .first(reviewPage.isFirst())
                .last(reviewPage.isLast())
                .build();
    }

    /**
     * 리뷰 작성
     */
    @Transactional
    public ReviewResponseDTO createReview(Long contentId, String username, ReviewRequest request) {
        Content content = contentRepository.findById(contentId)
                .orElseThrow(() -> new RuntimeException("Content not found: " + contentId));

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));

        // 이미 리뷰를 작성했는지 확인
        if (reviewRepository.existsByContentAndUser(content, user)) {
            throw new RuntimeException("이미 이 작품에 대한 리뷰를 작성하셨습니다.");
        }

        Review review = new Review();
        review.setContent(content);
        review.setUser(user);
        review.setRating(request.getRating());
        review.setTitle(request.getTitle());
        review.setReviewContent(request.getContent());

        Review saved = reviewRepository.save(review);
        
        // 동기화 로직 호출
        syncContentRating(contentId);
        
        return ReviewResponseDTO.from(saved, username);
    }

    /**
     * 리뷰 수정
     */
    @Transactional
    public ReviewResponseDTO updateReview(Long reviewId, String username, ReviewRequest request) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new RuntimeException("Review not found: " + reviewId));

        // 작성자 본인 확인
        if (!review.getUser().getUsername().equals(username)) {
            throw new RuntimeException("리뷰 수정 권한이 없습니다.");
        }

        review.updateReview(request.getRating(), request.getTitle(), request.getContent());
        Review updated = reviewRepository.save(review);

        // 동기화 로직 호출
        syncContentRating(updated.getContent().getContentId());

        return ReviewResponseDTO.from(updated, username);
    }

    /**
     * 리뷰 삭제
     */
    @Transactional
    public void deleteReview(Long reviewId, String username) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new RuntimeException("Review not found: " + reviewId));

        // 작성자 본인 확인
        if (!review.getUser().getUsername().equals(username)) {
            throw new RuntimeException("리뷰 삭제 권한이 없습니다.");
        }

        
        Long contentIdForSync = review.getContent().getContentId();
        reviewRepository.delete(review);
        
        // 삭제 쿼리가 먼저 나가도록 flush 강제 호출 (영속성 컨텍스트 비우기)
        reviewRepository.flush(); 
        
        // 동기화 로직 호출 (남은 리뷰 기준으로 재계산)
        syncContentRating(contentIdForSync);
    }

    /**
     * 내가 작성한 리뷰 목록 조회
     */
    public PageResponse<ReviewResponseDTO> getMyReviews(String username, Pageable pageable) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));

        Page<Review> reviewPage = reviewRepository.findByUser(user, pageable);

        List<ReviewResponseDTO> content = reviewPage.getContent().stream()
                .map(review -> ReviewResponseDTO.from(review, username))
                .collect(Collectors.toList());

        return PageResponse.<ReviewResponseDTO>builder()
                .content(content)
                .page(reviewPage.getNumber())
                .size(reviewPage.getSize())
                .totalElements(reviewPage.getTotalElements())
                .totalPages(reviewPage.getTotalPages())
                .first(reviewPage.isFirst())
                .last(reviewPage.isLast())
                .build();
    }
    
    // [✨ 최적화: 트랜잭션 동기화 방식의 역정규화]
    // 리뷰 작성/수정/삭제 후 같은 트랜잭션 내에서 이 메서드를 호출합니다.
    // 만약 updateRatingInfo 도중 DB 에러가 발생하면, 앞서 수행한 리뷰 관련 작업도
    // 모두 롤백(Rollback)되어 데이터 정합성(최종 일관성)이 100% 보장됩니다.
    /**
     * 리뷰 변경 시 해당 Content의 평균 평점과 리뷰 개수 동기화
     */
    private void syncContentRating(Long contentId) {
        // 1. 최신 통계 조회
        Double newAvg = reviewRepository.getAverageRatingByContentId(contentId);
        Long count = reviewRepository.countByContentId(contentId);
        
        // null 처리 (리뷰가 모두 지워졌을 때)
        Double finalAvg = newAvg != null ? newAvg : 0.0;
        Integer finalCount = count != null ? count.intValue() : 0;
        
        // 2. Content 원장 테이블 즉시 업데이트 (동기식)
        contentRepository.updateRatingInfo(contentId, finalAvg, finalCount);
    }
}


