package com.example.AOD.recommend.reaction;

/** 반응 변경 결과. previousState 는 되돌리기(§2-4)에 쓴다. */
public record ReactionResult(ReactionState state, ReactionState previousState, long likeCount, long dislikeCount) {
}
