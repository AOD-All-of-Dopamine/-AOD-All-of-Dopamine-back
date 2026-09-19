package com.example.AOD.recommend.context;

import java.util.UUID;

/** 요청에 딸린 추천 맥락. 서버 이벤트(reaction_changed 등)를 노출(impression)에 잇는 데 쓴다. */
public record RecContext(String source, UUID requestId, UUID impressionId, UUID anonId, UUID sessionId) {

    public static final RecContext EMPTY = new RecContext(null, null, null, null, null);

    /** 본문 값이 있으면 헤더 값을 덮어쓴다. */
    public RecContext withBody(String bodySource, String bodyRequestId, String bodyImpressionId) {
        UUID rid = parseUuid(bodyRequestId);
        UUID iid = parseUuid(bodyImpressionId);
        return new RecContext(
                bodySource != null && !bodySource.isBlank() ? bodySource : source,
                rid != null ? rid : requestId,
                iid != null ? iid : impressionId,
                anonId, sessionId);
    }

    /** 형식이 틀리면 null. 로그 맥락 때문에 요청을 실패시키지 않는다. */
    public static UUID parseUuid(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return UUID.fromString(s.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
