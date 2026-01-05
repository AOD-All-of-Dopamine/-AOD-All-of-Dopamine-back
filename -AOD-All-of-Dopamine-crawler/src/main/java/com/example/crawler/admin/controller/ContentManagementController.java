//package com.example.AOD.admin.controller;
//
//import com.example.crawler.common.dto.*;
//import com.example.crawler.common.service.ContentManagementService;
//import lombok.RequiredArgsConstructor;
//import org.springframework.data.domain.Page;
//import org.springframework.data.domain.PageRequest;
//import org.springframework.data.domain.Pageable;
//import org.springframework.data.domain.Sort;
//import org.springframework.http.HttpStatus;
//import org.springframework.http.ResponseEntity;
//import org.springframework.web.bind.annotation.*;
//
//import java.util.HashMap;
//import java.util.List;
//import java.util.Map;
//
//@RestController
//@RequestMapping("/api/v2/content")
//@RequiredArgsConstructor
//@CrossOrigin(origins = "*")
//public class ContentManagementController {
//
//    private final ContentManagementService contentManagementService;
//
//    /**
//     * 모든 콘텐츠 타입에서 검색
//     */
//    @GetMapping("/search")
//    public ResponseEntity<Map<String, List<ContentDTO>>> searchAllContent(
//            @RequestParam String keyword) {
//        Map<String, List<ContentDTO>> results = contentManagementService.searchAllContent(keyword);
//        return ResponseEntity.ok(results);
//    }
//
//    /**
//     * 특정 콘텐츠 타입에서 검색 (페이징)
//     */
//    /*@GetMapping("/{contentType}/search")
//    public ResponseEntity<Page<ContentDTO>> searchContentByType(
//            @PathVariable String contentType,
//            @RequestParam String keyword,
//            @RequestParam(defaultValue = "0") int page,
//            @RequestParam(defaultValue = "10") int size,
//            @RequestParam(defaultValue = "id") String sortBy,
//            @RequestParam(defaultValue = "DESC") String sortDirection) {
//
//        Sort.Direction direction = Sort.Direction.fromString(sortDirection);
//        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));
//
//        Page<ContentDTO> results = contentManagementService.searchContentByType(contentType, keyword, pageable);
//        return ResponseEntity.ok(results);
//    }*/
//
//    /**
//     * 콘텐츠 상세 정보 조회
//     */
//    @GetMapping("/{contentType}/{id}")
//    public ResponseEntity<ContentDetailDTO> getContentDetail(
//            @PathVariable String contentType,
//            @PathVariable Long id) {
//        ContentDetailDTO detail = contentManagementService.getContentDetail(contentType, id);
//        return ResponseEntity.ok(detail);
//    }
//
//    /**
//     * 플랫폼별 콘텐츠 조회
//     */
//    @GetMapping("/{contentType}/platform/{platform}")
//    public ResponseEntity<List<ContentDTO>> getContentByPlatform(
//            @PathVariable String contentType,
//            @PathVariable String platform) {
//        List<ContentDTO> contents = contentManagementService.getContentByPlatform(contentType, platform);
//        return ResponseEntity.ok(contents);
//    }
//
//    /**
//     * 멀티플랫폼 콘텐츠 조회
//     */
//    @GetMapping("/multi-platform")
//    public ResponseEntity<Map<String, List<ContentDTO>>> getMultiPlatformContent() {
//        Map<String, List<ContentDTO>> results = contentManagementService.getMultiPlatformContent();
//        return ResponseEntity.ok(results);
//    }
//
//    /**
//     * 플랫폼 독점 콘텐츠 조회
//     */
//    @GetMapping("/exclusive/{platform}")
//    public ResponseEntity<Map<String, List<ContentDTO>>> getExclusiveContent(
//            @PathVariable String platform) {
//        Map<String, List<ContentDTO>> results = contentManagementService.getExclusiveContent(platform);
//        return ResponseEntity.ok(results);
//    }
//
//    /**
//     * 통계 정보 조회
//     */
//    @GetMapping("/statistics")
//    public ResponseEntity<Map<String, Object>> getStatistics() {
//        Map<String, Object> stats = contentManagementService.getStatistics();
//        return ResponseEntity.ok(stats);
//    }
//
//    /**
//     * 콘텐츠 생성 또는 업데이트
//     */
//    @PostMapping
//    public ResponseEntity<ContentDetailDTO> createOrUpdateContent(
//            @RequestBody ContentManageDTO dto) {
//        ContentDetailDTO result = contentManagementService.createOrUpdateContent(dto);
//        return ResponseEntity.status(HttpStatus.CREATED).body(result);
//    }
//
//    /**
//     * 콘텐츠 업데이트
//     */
//    @PutMapping("/{contentType}/{id}")
//    public ResponseEntity<ContentDetailDTO> updateContent(
//            @PathVariable String contentType,
//            @PathVariable Long id,
//            @RequestBody ContentManageDTO dto) {
//        dto.setId(id);
//        dto.setContentType(contentType);
//        ContentDetailDTO result = contentManagementService.createOrUpdateContent(dto);
//        return ResponseEntity.ok(result);
//    }
//
//    /**
//     * 콘텐츠 삭제
//     */
//    @DeleteMapping("/{contentType}/{id}")
//    public ResponseEntity<Void> deleteContent(
//            @PathVariable String contentType,
//            @PathVariable Long id) {
//        contentManagementService.deleteContent(contentType, id);
//        return ResponseEntity.noContent().build();
//    }
//
//    /**
//     * 특정 플랫폼의 콘텐츠 수 조회
//     */
//    @GetMapping("/count/platform/{platform}")
//    public ResponseEntity<Map<String, Long>> getContentCountByPlatform(
//            @PathVariable String platform) {
//        // 각 콘텐츠 타입별로 해당 플랫폼의 콘텐츠 수를 계산
//        Map<String, Long> counts = new HashMap<>();
//
//        try {
//            List<ContentDTO> novels = contentManagementService.getContentByPlatform("novel", platform);
//            counts.put("novels", (long) novels.size());
//        } catch (Exception e) {
//            counts.put("novels", 0L);
//        }
//
//        try {
//            List<ContentDTO> movies = contentManagementService.getContentByPlatform("movie", platform);
//            counts.put("movies", (long) movies.size());
//        } catch (Exception e) {
//            counts.put("movies", 0L);
//        }
//
//        try {
//            List<ContentDTO> otts = contentManagementService.getContentByPlatform("ott", platform);
//            counts.put("otts", (long) otts.size());
//        } catch (Exception e) {
//            counts.put("otts", 0L);
//        }
//
//        try {
//            List<ContentDTO> webtoons = contentManagementService.getContentByPlatform("webtoon", platform);
//            counts.put("webtoons", (long) webtoons.size());
//        } catch (Exception e) {
//            counts.put("webtoons", 0L);
//        }
//
//        try {
//            List<ContentDTO> games = contentManagementService.getContentByPlatform("game", platform);
//            counts.put("games", (long) games.size());
//        } catch (Exception e) {
//            counts.put("games", 0L);
//        }
//
//        long total = counts.values().stream().mapToLong(Long::longValue).sum();
//        counts.put("total", total);
//
//        return ResponseEntity.ok(counts);
//    }
//}

