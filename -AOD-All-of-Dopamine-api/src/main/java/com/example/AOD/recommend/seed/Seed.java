package com.example.AOD.recommend.seed;

import java.time.LocalDateTime;

/** 시드 하나. source 는 이유 문구(§2-3)에도 쓰이므로 like·bookmark·review 문자열을 그대로 쓴다. */
public record Seed(Long contentId, String source, LocalDateTime at) {

    public static final String LIKE = "like";
    public static final String BOOKMARK = "bookmark";
    public static final String REVIEW = "review";
}
