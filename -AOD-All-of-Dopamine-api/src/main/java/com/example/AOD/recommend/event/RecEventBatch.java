package com.example.AOD.recommend.event;

import java.util.List;

/** POST /api/rec-events 본문. 식별자는 헤더가 아니라 본문에 — sendBeacon 은 헤더를 실을 수 없다. */
public record RecEventBatch(String anonId, String sessionId, String appVersion, String device,
                            List<RecEventItem> events) {
}
