package com.example.AOD.recommend.card;

import com.example.AOD.recommend.router.dto.RouterItem;

/**
 * 버퍼에서 탈락한 후보 (rec_item_served.is_served=false · dropped_reason).
 * 무엇이 왜 빠졌는지 없으면 커버리지 문제를 추적할 수 없다(REC_TAB_DESIGN §4-2, Rules of ML #6).
 * contentId 는 DB 에 없어서 버린 경우 null 이다.
 */
public record DroppedCandidate(RouterItem item, Long contentId, String reason) {

    /** 키에 맞는 platform_data 행이 없다 — 체인 skipped_keys 로 기억한다. */
    public static final String NOT_IN_DB = "not_in_db";
    /** 성인 작품 — 체인 skipped_keys 로 기억한다. */
    public static final String ADULT = "adult";
    /** 이미 이 응답이나 체인 seen 에 있는 작품 — seen 으로 이미 빠지므로 기억하지 않는다. */
    public static final String DUP_CONTENT = "dup_content";
    /** 정원을 채운 뒤 남은 후보 — 다음 쪽에서 쓸 수 있으므로 기억하지 않는다. */
    public static final String OVER_K = "over_k";
}
