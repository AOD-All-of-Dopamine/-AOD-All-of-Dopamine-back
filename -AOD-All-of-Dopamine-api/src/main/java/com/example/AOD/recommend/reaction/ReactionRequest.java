package com.example.AOD.recommend.reaction;

/** PUT /api/works/{id}/reaction 본문. state 외에는 전부 선택. */
public record ReactionRequest(String state, String source, String requestId, String impressionId) {
}
