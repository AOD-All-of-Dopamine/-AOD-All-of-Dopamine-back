package com.example.AOD.api.controller;

import com.example.AOD.api.dto.PageResponse;
import com.example.AOD.api.dto.WorkResponseDTO;
import com.example.AOD.api.dto.WorkSummaryDTO;
import com.example.AOD.api.service.WorkApiService;
import com.example.AOD.domain.entity.Domain;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/works")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:3000")
public class WorkController {

    private final WorkApiService workApiService;

    /**
     * 작품 목록 조회
     * GET /api/works?domain=GAME&keyword=검색어&page=0&size=20&sort=masterTitle,asc
     */
    @GetMapping
    public ResponseEntity<PageResponse<WorkSummaryDTO>> getWorks(
            @RequestParam(required = false) String domain,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "masterTitle") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDirection
    ) {
        Domain domainEnum = null;
        if (domain != null && !domain.isBlank()) {
            try {
                domainEnum = Domain.valueOf(domain.toUpperCase());
            } catch (IllegalArgumentException e) {
                log.warn("Invalid domain parameter: {}", domain);
            }
        }

        Sort sort = Sort.by(Sort.Direction.fromString(sortDirection), sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);

        PageResponse<WorkSummaryDTO> response = workApiService.getWorks(domainEnum, keyword, pageable);
        return ResponseEntity.ok(response);
    }

    /**
     * 작품 상세 조회
     * GET /api/works/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<WorkResponseDTO> getWorkDetail(@PathVariable Long id) {
        WorkResponseDTO response = workApiService.getWorkDetail(id);
        return ResponseEntity.ok(response);
    }

    /**
     * 최근 출시작 조회 (신작)
     * GET /api/releases/recent?domain=GAME&page=0&size=20
     */
    @GetMapping("/releases/recent")
    public ResponseEntity<PageResponse<WorkSummaryDTO>> getRecentReleases(
            @RequestParam(required = false) String domain,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Domain domainEnum = null;
        if (domain != null && !domain.isBlank()) {
            try {
                domainEnum = Domain.valueOf(domain.toUpperCase());
            } catch (IllegalArgumentException e) {
                log.warn("Invalid domain parameter: {}", domain);
            }
        }

        Pageable pageable = PageRequest.of(page, size);
        PageResponse<WorkSummaryDTO> response = workApiService.getRecentReleases(domainEnum, pageable);
        return ResponseEntity.ok(response);
    }

    /**
     * 출시 예정작 조회
     * GET /api/releases/upcoming?domain=GAME&page=0&size=20
     */
    @GetMapping("/releases/upcoming")
    public ResponseEntity<PageResponse<WorkSummaryDTO>> getUpcomingReleases(
            @RequestParam(required = false) String domain,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Domain domainEnum = null;
        if (domain != null && !domain.isBlank()) {
            try {
                domainEnum = Domain.valueOf(domain.toUpperCase());
            } catch (IllegalArgumentException e) {
                log.warn("Invalid domain parameter: {}", domain);
            }
        }

        Pageable pageable = PageRequest.of(page, size);
        PageResponse<WorkSummaryDTO> response = workApiService.getUpcomingReleases(domainEnum, pageable);
        return ResponseEntity.ok(response);
    }
}
