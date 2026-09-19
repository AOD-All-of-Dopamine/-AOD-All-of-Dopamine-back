package com.example.AOD.recommend.notinterested;

/** 관심 없음 요청 본문 (선택). 노출과 이어 붙일 식별자만 담는다 (REC_TAB_DESIGN §4-1). */
public record NotInterestedRequest(String requestId, String impressionId) { }
