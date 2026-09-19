package com.example.AOD.recommend.reason;

import com.example.AOD.recommend.seed.Seed;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ReasonBuilderTest {

    private final ReasonBuilder builder = new ReasonBuilder();

    @Test
    void josaFollowsFinalConsonant() {
        assertEquals("를", ReasonBuilder.josa("코코"));        // 받침 없음
        assertEquals("을", ReasonBuilder.josa("진격의 거인")); // 받침 있음
        assertEquals("를", ReasonBuilder.josa("나루토"));
        assertEquals("을", ReasonBuilder.josa("검"));
    }

    @Test
    void nonHangulTailUsesBothForms() {
        assertEquals("을(를)", ReasonBuilder.josa("Elden Ring"));
        assertEquals("을(를)", ReasonBuilder.josa("Portal 2"));
        assertEquals("을(를)", ReasonBuilder.josa("전지적 독자 시점!"));
        assertEquals("을(를)", ReasonBuilder.josa("ﾃｽﾄ"));
    }

    @Test
    void buildsOneLinePerSource() {
        assertEquals("코코를 좋아해서", builder.build(Seed.LIKE, 1L, "코코").text());
        assertEquals("코코를 담아둬서", builder.build(Seed.BOOKMARK, 1L, "코코").text());
        assertEquals("코코를 높게 평가해서", builder.build(Seed.REVIEW, 1L, "코코").text());
        assertEquals("Elden Ring을(를) 좋아해서", builder.build(Seed.LIKE, 2L, "Elden Ring").text());
    }

    @Test
    void reasonCarriesTypeAndSeedContentId() {
        RecReason reason = builder.build(Seed.REVIEW, 42L, "진격의 거인");

        assertEquals("review", reason.type());
        assertEquals(42L, reason.seedContentId());
        assertEquals("진격의 거인을 높게 평가해서", reason.text());
    }

    @Test
    void unusableInputGivesNoReason() {
        assertNull(builder.build(null, 1L, "코코"));
        assertNull(builder.build("exploration", 1L, "코코"), "탐색 칸은 이번 범위가 아니다");
        assertNull(builder.build(Seed.LIKE, null, "코코"));
        assertNull(builder.build(Seed.LIKE, 1L, null));
        assertNull(builder.build(Seed.LIKE, 1L, "   "));
    }

    @Test
    void titleIsTrimmedBeforeJosa() {
        assertEquals("코코를 좋아해서", builder.build(Seed.LIKE, 1L, "  코코  ").text());
    }
}
