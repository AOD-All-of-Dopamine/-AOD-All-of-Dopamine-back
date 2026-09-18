package com.example.AOD.recommend.event;

import java.util.Map;

public record RecEventItem(String eventId, String type, String clientTs, String requestId, String impressionId,
                           Long contentId, String surface, Map<String, Object> payload) {
}
