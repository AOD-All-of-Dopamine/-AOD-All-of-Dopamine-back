package com.example.AOD.recommend.reaction;

import com.example.AOD.domain.ContentLike;

/** 작품에 대한 사용자 반응. NONE = content_likes 에 행이 없음. */
public enum ReactionState {
    LIKE, DISLIKE, NONE;

    public static ReactionState fromLikeType(ContentLike.LikeType type) {
        if (type == null) throw new IllegalArgumentException("LikeType 이 null — content_likes.like_type 은 NOT NULL 이어야 한다");
        return type == ContentLike.LikeType.LIKE ? LIKE : DISLIKE;
    }

    /** NONE 에는 대응하는 LikeType 이 없다 — 호출 전에 NONE 을 걸러야 한다. */
    public ContentLike.LikeType toLikeType() {
        if (this == NONE) throw new IllegalStateException("NONE 은 LikeType 으로 바꿀 수 없다");
        return this == LIKE ? ContentLike.LikeType.LIKE : ContentLike.LikeType.DISLIKE;
    }
}
