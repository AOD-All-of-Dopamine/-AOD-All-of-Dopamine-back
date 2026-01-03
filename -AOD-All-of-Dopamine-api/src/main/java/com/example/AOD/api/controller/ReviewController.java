package com.example.AOD.api.controller;

import com.example.AOD.api.dto.PageResponse;
import com.example.AOD.api.dto.review.ReviewRequest;
import com.example.AOD.api.dto.review.ReviewResponseDTO;
import com.example.AOD.api.service.ReviewService;
import com.example.AOD.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;
    private final JwtTokenProvider jwtTokenProvider;

    /**
     * 특정 작품의 리뷰 목록 조회
     * GET /api/works/{contentId}/reviews?page=0&size=20
     */
    @GetMapping("/works/{contentId}/reviews")
    public ResponseEntity<PageResponse<ReviewResponseDTO>> getReviews(
            @PathVariable Long contentId,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        String username = extractUsername(authHeader);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        
        PageResponse<ReviewResponseDTO> response = reviewService.getReviewsByContentId(contentId, username, pageable);
        return ResponseEntity.ok(response);
    }

    /**
     * 리뷰 작성
     * POST /api/works/{contentId}/reviews
     */
    @PostMapping("/works/{contentId}/reviews")
    public ResponseEntity<?> createReview(
            @PathVariable Long contentId,
            @RequestHeader("Authorization") String authHeader,
            @RequestBody ReviewRequest request
    ) {
        try {
            String username = extractUsernameRequired(authHeader);
            ReviewResponseDTO response = reviewService.createReview(contentId, username, request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("리뷰 작성 실패", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 리뷰 수정
     * PUT /api/reviews/{reviewId}
     */
    @PutMapping("/reviews/{reviewId}")
    public ResponseEntity<?> updateReview(
            @PathVariable Long reviewId,
            @RequestHeader("Authorization") String authHeader,
            @RequestBody ReviewRequest request
    ) {
        try {
            String username = extractUsernameRequired(authHeader);
            ReviewResponseDTO response = reviewService.updateReview(reviewId, username, request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("리뷰 수정 실패", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 리뷰 삭제
     * DELETE /api/reviews/{reviewId}
     */
    @DeleteMapping("/reviews/{reviewId}")
    public ResponseEntity<?> deleteReview(
            @PathVariable Long reviewId,
            @RequestHeader("Authorization") String authHeader
    ) {
        try {
            String username = extractUsernameRequired(authHeader);
            reviewService.deleteReview(reviewId, username);
            return ResponseEntity.ok(Map.of("message", "리뷰가 삭제되었습니다."));
        } catch (Exception e) {
            log.error("리뷰 삭제 실패", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 내가 작성한 리뷰 목록 조회
     * GET /api/my/reviews?page=0&size=20
     */
    @GetMapping("/my/reviews")
    public ResponseEntity<?> getMyReviews(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        try {
            String username = extractUsernameRequired(authHeader);
            Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
            
            PageResponse<ReviewResponseDTO> response = reviewService.getMyReviews(username, pageable);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("내 리뷰 조회 실패", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // 헬퍼 메서드
    private String extractUsername(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }
        try {
            String token = authHeader.substring(7);
            return jwtTokenProvider.getUsername(token);
        } catch (Exception e) {
            return null;
        }
    }

    private String extractUsernameRequired(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new RuntimeException("인증이 필요합니다.");
        }
        String token = authHeader.substring(7);
        return jwtTokenProvider.getUsername(token);
    }
}


