package com.example.AOD.recommend.api.dto;

import com.example.AOD.api.dto.WorkSummaryDTO;
import com.example.AOD.recommend.reason.RecReason;

/**
 * 추천 카드 1장 (REC_TAB_DESIGN §4-1).
 * impressionId 는 이 카드 노출의 식별자다 — 응답과 rec_item_served 가 같은 값을 쓰고,
 * 프론트가 클릭·관심 없음 이벤트를 이 값으로 되돌려 준다.
 * reason 은 대체 목록과 주도 시드를 모르는 카드에서 null 이다.
 */
public record RecommendItem(String impressionId, int rank, WorkSummaryDTO work, RecReason reason) { }
