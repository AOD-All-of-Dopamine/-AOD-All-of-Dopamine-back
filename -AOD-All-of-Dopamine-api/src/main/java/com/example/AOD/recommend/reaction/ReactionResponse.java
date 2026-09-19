package com.example.AOD.recommend.reaction;

public record ReactionResponse(String state, String previousState, long likeCount, long dislikeCount) {
}
