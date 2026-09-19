package com.example.AOD.recommend.api.dto;

import java.util.List;

/**
 * GET /api/recommendations 200 본문 (REC_TAB_DESIGN §4-1).
 * 대체 응답도 같은 모양이다 — fallback=true · fallbackReason · reason=null · hasMore=false.
 * 대체의 chainId 는 저장하지 않은 새 UUID 다(로그 상관용).
 */
public record RecommendResponse(String requestId, String chainId, int pageDepth,
                                boolean fallback, String fallbackReason,
                                List<RecommendItem> items, boolean hasMore) { }
