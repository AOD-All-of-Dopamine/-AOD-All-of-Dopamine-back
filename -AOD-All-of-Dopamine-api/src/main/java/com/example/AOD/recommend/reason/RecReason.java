package com.example.AOD.recommend.reason;

/**
 * 카드에 붙는 추천 이유 (REC_TAB_DESIGN §2-3 · §4-1).
 * type 은 like·bookmark·review. 대체 목록과 주도 시드를 모르는 카드는 reason 자체가 null 이다.
 */
public record RecReason(String type, Long seedContentId, String text) { }
