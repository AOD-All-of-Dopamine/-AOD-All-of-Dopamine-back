package com.example.AOD.api.controller;

import com.example.AOD.api.dto.PageResponse;
import com.example.AOD.api.dto.WorkSummaryDTO;
import com.example.AOD.api.service.BookmarkService;
import com.example.AOD.api.service.LikeService;
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
public class InteractionController {

    private final LikeService likeService;
    private final BookmarkService bookmarkService;
    private final JwtTokenProvider jwtTokenProvider;

    // ========== 좋아요/싫어요 API ==========

    /**
     * 좋아요 토글
     * POST /api/works/{contentId}/like
     */
    @PostMapping("/works/{contentId}/like")
    public ResponseEntity<?> toggleLike(
            @PathVariable Long contentId,
            @RequestHeader("Authorization") String authHeader
    ) {
        try {
            String username = extractUsernameRequired(authHeader);
            Map<String, Object> response = likeService.toggleLike(contentId, username);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("좋아요 토글 실패", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 싫어요 토글
     * POST /api/works/{contentId}/dislike
     */
    @PostMapping("/works/{contentId}/dislike")
    public ResponseEntity<?> toggleDislike(
            @PathVariable Long contentId,
            @RequestHeader("Authorization") String authHeader
    ) {
        try {
            String username = extractUsernameRequired(authHeader);
            Map<String, Object> response = likeService.toggleDislike(contentId, username);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("싫어요 토글 실패", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 좋아요/싫어요 통계 조회
     * GET /api/works/{contentId}/likes
     */
    @GetMapping("/works/{contentId}/likes")
    public ResponseEntity<?> getLikeStats(
            @PathVariable Long contentId,
            @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        try {
            String username = extractUsername(authHeader);
            Map<String, Object> response = likeService.getLikeStats(contentId, username);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("좋아요 통계 조회 실패", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ========== 북마크 API ==========

    /**
     * 북마크 토글
     * POST /api/works/{contentId}/bookmark
     */
    @PostMapping("/works/{contentId}/bookmark")
    public ResponseEntity<?> toggleBookmark(
            @PathVariable Long contentId,
            @RequestHeader("Authorization") String authHeader
    ) {
        try {
            String username = extractUsernameRequired(authHeader);
            Map<String, Object> response = bookmarkService.toggleBookmark(contentId, username);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("북마크 토글 실패", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 내 북마크 목록 조회
     * GET /api/my/bookmarks?page=0&size=20
     */
    @GetMapping("/my/bookmarks")
    public ResponseEntity<?> getMyBookmarks(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        try {
            String username = extractUsernameRequired(authHeader);
            Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
            
            PageResponse<WorkSummaryDTO> response = bookmarkService.getMyBookmarks(username, pageable);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("북마크 목록 조회 실패", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 내가 좋아요한 작품 목록 조회
     * GET /api/my/likes?page=0&size=20
     */
    @GetMapping("/my/likes")
    public ResponseEntity<?> getMyLikes(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        try {
            String username = extractUsernameRequired(authHeader);
            Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
            
            PageResponse<WorkSummaryDTO> response = likeService.getMyLikes(username, pageable);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("좋아요 목록 조회 실패", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 북마크 여부 확인
     * GET /api/works/{contentId}/bookmark
     */
    @GetMapping("/works/{contentId}/bookmark")
    public ResponseEntity<?> getBookmarkStatus(
            @PathVariable Long contentId,
            @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        try {
            String username = extractUsername(authHeader);
            Map<String, Object> response = bookmarkService.getBookmarkStatus(contentId, username);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("북마크 상태 조회 실패", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ========== 헬퍼 메서드 ==========

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


