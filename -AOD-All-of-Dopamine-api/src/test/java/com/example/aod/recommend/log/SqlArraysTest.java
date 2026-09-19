package com.example.AOD.recommend.log;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SqlArraysTest {

    @Test
    void bigintLiteral() {
        assertEquals("{}", SqlArrays.bigints(null));
        assertEquals("{}", SqlArrays.bigints(List.of()));
        assertEquals("{1}", SqlArrays.bigints(List.of(1L)));
        assertEquals("{1,2,3}", SqlArrays.bigints(List.of(1L, 2L, 3L)));
        assertEquals("{7}", SqlArrays.bigints(Arrays.asList(null, 7L)), "null 원소는 건너뛴다");
        assertEquals("{}", SqlArrays.bigints(Arrays.asList((Long) null)));
    }

    @Test
    void textLiteralQuotesAndEscapes() {
        assertEquals("{}", SqlArrays.texts(null));
        assertEquals("{}", SqlArrays.texts(List.of()));
        assertEquals("{\"steam:730\"}", SqlArrays.texts(List.of("steam:730")));
        assertEquals("{\"steam:730\",\"tmdb:movie_603\"}",
                SqlArrays.texts(List.of("steam:730", "tmdb:movie_603")));
        assertEquals("{\"a\\\"b\"}", SqlArrays.texts(List.of("a\"b")), "큰따옴표는 이스케이프");
        assertEquals("{\"a\\\\b\"}", SqlArrays.texts(List.of("a\\b")), "역슬래시는 이스케이프");
        assertEquals("{\"x\"}", SqlArrays.texts(Arrays.asList(null, "x")));
    }
}
