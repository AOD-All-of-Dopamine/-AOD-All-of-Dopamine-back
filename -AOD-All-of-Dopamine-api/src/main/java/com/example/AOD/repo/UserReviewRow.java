package com.example.AOD.repo;

import java.time.LocalDateTime;

/** 사용자 리뷰 경량 행. at 은 updated_at (시드 정렬 기준, REC_TAB_DESIGN §6-4). */
public record UserReviewRow(Long contentId, Double rating, LocalDateTime at) { }
