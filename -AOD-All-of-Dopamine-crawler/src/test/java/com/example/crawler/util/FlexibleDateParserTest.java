package com.example.crawler.util;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class FlexibleDateParserTest {

    @Test
    void parsesAllSupportedFormats() {
        assertEquals(LocalDate.of(1998, 11, 19), FlexibleDateParser.parse("1998년 11월 19일"));
        assertEquals(LocalDate.of(2024, 3, 5), FlexibleDateParser.parse("2024-03-05"));
        assertEquals(LocalDate.of(2024, 3, 5), FlexibleDateParser.parse("2024.03.05"));
        assertEquals(LocalDate.of(2024, 3, 5), FlexibleDateParser.parse("2024/03/05"));
        assertEquals(LocalDate.of(1998, 11, 19), FlexibleDateParser.parse("Nov 19, 1998"));
    }

    @Test
    void yearOnlyBecomesJanuaryFirst() {
        assertEquals(LocalDate.of(2020, 1, 1), FlexibleDateParser.parse("2020"));
        assertEquals(LocalDate.of(2020, 1, 1), FlexibleDateParser.parse(2020));
    }

    @Test
    void passesThroughLocalDateAndHandlesGarbage() {
        LocalDate d = LocalDate.of(2025, 5, 5);
        assertEquals(d, FlexibleDateParser.parse(d));
        assertNull(FlexibleDateParser.parse(null));
        assertNull(FlexibleDateParser.parse("날짜아님"));
    }
}
