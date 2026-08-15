package com.example.AOD.api.controller;

import com.example.AOD.api.dto.PageResponse;
import com.example.AOD.api.dto.collection.*;
import com.example.AOD.api.service.CollectionService;
import com.example.AOD.security.JwtTokenProvider;
import com.example.shared.entity.Domain;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 컬렉션 API (2026-08 플랜의 API 계약).
 * 인증: 기존 패턴(Authorization 헤더 + JwtTokenProvider) — 비로그인 열람 표면은 헤더 optional.
 * 에러: 기존 관례(400 + error 맵) + 계약상 상태(403/404/409)는 ResponseStatusException으로 구분.
 */
@Slf4j
@RestController
@RequestMapping("/api/collections")
@RequiredArgsConstructor
public class CollectionController {

    private final CollectionService collectionService;
    private final JwtTokenProvider jwtTokenProvider;

    /**
     * 공개 컬렉션 목록 (발견 페이지)
     * GET /api/collections?sort=popular|latest&domain=&page=&size=
     */
    @GetMapping
    public ResponseEntity<?> getPublicCollections(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam(defaultValue = "popular") String sort,
            @RequestParam(required = false) String domain,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        try {
            String username = extractUsername(authHeader);
            Domain domainEnum = parseDomain(domain);
            PageResponse<CollectionSummaryDTO> response =
                    collectionService.getPublicCollections(sort, domainEnum, username, page, size);
            return ResponseEntity.ok(response);
        } catch (ResponseStatusException e) {
            return toErrorResponse(e);
        } catch (Exception e) {
            log.error("컬렉션 목록 조회 실패", e);
            return badRequest(e);
        }
    }

    /**
     * 내 컬렉션 목록 (비공개 포함)
     * GET /api/collections/mine
     */
    @GetMapping("/mine")
    public ResponseEntity<?> getMyCollections(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        try {
            String username = extractUsernameRequired(authHeader);
            PageResponse<CollectionSummaryDTO> response =
                    collectionService.getMyCollections(username, page, size);
            return ResponseEntity.ok(response);
        } catch (ResponseStatusException e) {
            return toErrorResponse(e);
        } catch (Exception e) {
            log.error("내 컬렉션 조회 실패", e);
            return badRequest(e);
        }
    }

    /**
     * 담기 팝오버용 — 내 컬렉션 + 각각에 해당 작품 포함 여부
     * GET /api/collections/mine/summary?contentId=
     */
    @GetMapping("/mine/summary")
    public ResponseEntity<?> getMyCollectionSummaries(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam Long contentId
    ) {
        try {
            String username = extractUsernameRequired(authHeader);
            List<MyCollectionSummaryDTO> response =
                    collectionService.getMyCollectionSummaries(username, contentId);
            return ResponseEntity.ok(response);
        } catch (ResponseStatusException e) {
            return toErrorResponse(e);
        } catch (Exception e) {
            log.error("내 컬렉션 요약 조회 실패", e);
            return badRequest(e);
        }
    }

    /**
     * 컬렉션 상세 + 조회수 +1 (PRIVATE는 소유자만, 그 외 403)
     * GET /api/collections/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getCollectionDetail(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        try {
            String username = extractUsername(authHeader);
            CollectionDetailDTO response = collectionService.getCollectionDetail(id, username);
            return ResponseEntity.ok(response);
        } catch (ResponseStatusException e) {
            return toErrorResponse(e);
        } catch (Exception e) {
            log.error("컬렉션 상세 조회 실패", e);
            return badRequest(e);
        }
    }

    /**
     * 컬렉션 생성
     * POST /api/collections
     */
    @PostMapping
    public ResponseEntity<?> createCollection(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody CollectionCreateRequest request
    ) {
        try {
            String username = extractUsernameRequired(authHeader);
            CollectionSummaryDTO response = collectionService.createCollection(username, request);
            return ResponseEntity.ok(response);
        } catch (ResponseStatusException e) {
            return toErrorResponse(e);
        } catch (Exception e) {
            log.error("컬렉션 생성 실패", e);
            return badRequest(e);
        }
    }

    /**
     * 컬렉션 수정 (소유자만)
     * PATCH /api/collections/{id}
     */
    @PatchMapping("/{id}")
    public ResponseEntity<?> updateCollection(
            @PathVariable Long id,
            @RequestHeader("Authorization") String authHeader,
            @RequestBody CollectionUpdateRequest request
    ) {
        try {
            String username = extractUsernameRequired(authHeader);
            CollectionSummaryDTO response = collectionService.updateCollection(id, username, request);
            return ResponseEntity.ok(response);
        } catch (ResponseStatusException e) {
            return toErrorResponse(e);
        } catch (Exception e) {
            log.error("컬렉션 수정 실패", e);
            return badRequest(e);
        }
    }

    /**
     * 컬렉션 삭제 (소유자만)
     * DELETE /api/collections/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteCollection(
            @PathVariable Long id,
            @RequestHeader("Authorization") String authHeader
    ) {
        try {
            String username = extractUsernameRequired(authHeader);
            collectionService.deleteCollection(id, username);
            return ResponseEntity.ok(Map.of("message", "컬렉션이 삭제되었습니다."));
        } catch (ResponseStatusException e) {
            return toErrorResponse(e);
        } catch (Exception e) {
            log.error("컬렉션 삭제 실패", e);
            return badRequest(e);
        }
    }

    /**
     * 아이템 추가 — 말미 position, 도메인 불일치 400, 중복 409 (소유자만)
     * POST /api/collections/{id}/items
     */
    @PostMapping("/{id}/items")
    public ResponseEntity<?> addItem(
            @PathVariable Long id,
            @RequestHeader("Authorization") String authHeader,
            @RequestBody CollectionItemAddRequest request
    ) {
        try {
            String username = extractUsernameRequired(authHeader);
            CollectionItemDTO response = collectionService.addItem(id, username, request);
            return ResponseEntity.ok(response);
        } catch (ResponseStatusException e) {
            return toErrorResponse(e);
        } catch (Exception e) {
            log.error("컬렉션 아이템 추가 실패", e);
            return badRequest(e);
        }
    }

    /**
     * 아이템 수정 — 코멘트/위치 (소유자만)
     * PATCH /api/collections/{id}/items/{itemId}
     */
    @PatchMapping("/{id}/items/{itemId}")
    public ResponseEntity<?> updateItem(
            @PathVariable Long id,
            @PathVariable Long itemId,
            @RequestHeader("Authorization") String authHeader,
            @RequestBody CollectionItemUpdateRequest request
    ) {
        try {
            String username = extractUsernameRequired(authHeader);
            CollectionItemDTO response = collectionService.updateItem(id, itemId, username, request);
            return ResponseEntity.ok(response);
        } catch (ResponseStatusException e) {
            return toErrorResponse(e);
        } catch (Exception e) {
            log.error("컬렉션 아이템 수정 실패", e);
            return badRequest(e);
        }
    }

    /**
     * 아이템 삭제 (소유자만)
     * DELETE /api/collections/{id}/items/{itemId}
     */
    @DeleteMapping("/{id}/items/{itemId}")
    public ResponseEntity<?> deleteItem(
            @PathVariable Long id,
            @PathVariable Long itemId,
            @RequestHeader("Authorization") String authHeader
    ) {
        try {
            String username = extractUsernameRequired(authHeader);
            collectionService.deleteItem(id, itemId, username);
            return ResponseEntity.ok(Map.of("message", "아이템이 삭제되었습니다."));
        } catch (ResponseStatusException e) {
            return toErrorResponse(e);
        } catch (Exception e) {
            log.error("컬렉션 아이템 삭제 실패", e);
            return badRequest(e);
        }
    }

    /**
     * 아이템 일괄 재정렬 (소유자만)
     * PUT /api/collections/{id}/items/order
     */
    @PutMapping("/{id}/items/order")
    public ResponseEntity<?> reorderItems(
            @PathVariable Long id,
            @RequestHeader("Authorization") String authHeader,
            @RequestBody CollectionOrderRequest request
    ) {
        try {
            String username = extractUsernameRequired(authHeader);
            collectionService.reorderItems(id, username, request.getItemIds());
            return ResponseEntity.ok(Map.of("message", "정렬이 저장되었습니다."));
        } catch (ResponseStatusException e) {
            return toErrorResponse(e);
        } catch (Exception e) {
            log.error("컬렉션 아이템 재정렬 실패", e);
            return badRequest(e);
        }
    }

    /**
     * 좋아요 (멱등)
     * POST /api/collections/{id}/like
     */
    @PostMapping("/{id}/like")
    public ResponseEntity<?> like(
            @PathVariable Long id,
            @RequestHeader("Authorization") String authHeader
    ) {
        try {
            String username = extractUsernameRequired(authHeader);
            Map<String, Object> response = collectionService.like(id, username);
            return ResponseEntity.ok(response);
        } catch (ResponseStatusException e) {
            return toErrorResponse(e);
        } catch (Exception e) {
            log.error("컬렉션 좋아요 실패", e);
            return badRequest(e);
        }
    }

    /**
     * 좋아요 취소 (멱등)
     * DELETE /api/collections/{id}/like
     */
    @DeleteMapping("/{id}/like")
    public ResponseEntity<?> unlike(
            @PathVariable Long id,
            @RequestHeader("Authorization") String authHeader
    ) {
        try {
            String username = extractUsernameRequired(authHeader);
            Map<String, Object> response = collectionService.unlike(id, username);
            return ResponseEntity.ok(response);
        } catch (ResponseStatusException e) {
            return toErrorResponse(e);
        } catch (Exception e) {
            log.error("컬렉션 좋아요 취소 실패", e);
            return badRequest(e);
        }
    }

    // ========== 헬퍼 메서드 ==========

    private static Domain parseDomain(String domain) {
        if (domain == null || domain.isBlank()) return null;
        try {
            return Domain.valueOf(domain.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "지원하지 않는 도메인입니다: " + domain);
        }
    }

    private ResponseEntity<Map<String, String>> toErrorResponse(ResponseStatusException e) {
        String message = e.getReason() != null ? e.getReason() : "요청을 처리할 수 없습니다.";
        return ResponseEntity.status(e.getStatusCode()).body(Map.of("error", message));
    }

    private ResponseEntity<Map<String, String>> badRequest(Exception e) {
        String message = e.getMessage() != null ? e.getMessage() : "요청을 처리할 수 없습니다.";
        return ResponseEntity.badRequest().body(Map.of("error", message));
    }

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
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "인증이 필요합니다.");
        }
        try {
            String token = authHeader.substring(7);
            return jwtTokenProvider.getUsername(token);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다.");
        }
    }
}
