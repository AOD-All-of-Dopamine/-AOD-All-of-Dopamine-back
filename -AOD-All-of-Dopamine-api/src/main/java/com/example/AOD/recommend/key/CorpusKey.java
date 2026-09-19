package com.example.AOD.recommend.key;

/** 추천 라우터가 쓰는 키 하나. platform 은 steam·tmdb·webtoon·webnovel 중 하나, key 는 문자열이다. */
public record CorpusKey(String platform, String key) { }
