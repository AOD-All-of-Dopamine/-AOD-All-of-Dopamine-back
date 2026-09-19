package com.example.AOD.recommend.reason;

import com.example.AOD.recommend.seed.Seed;
import org.springframework.stereotype.Component;

import java.util.Map;

/** 주도 시드 → 이유 한 줄 (REC_TAB_DESIGN §2-3). 상태가 없다. */
@Component
public class ReasonBuilder {

    private static final char HANGUL_FIRST = 0xAC00;
    private static final char HANGUL_LAST = 0xD7A3;
    /** 한글 음절 한 글자는 (초성, 중성, 종성 28가지) 조합이다 — 종성 자리가 0이면 받침이 없다. */
    private static final int JONGSEONG_COUNT = 28;

    private static final Map<String, String> SUFFIX_BY_SOURCE = Map.of(
            Seed.LIKE, " 좋아해서",
            Seed.BOOKMARK, " 담아둬서",
            Seed.REVIEW, " 높게 평가해서");

    /** 쓸 수 없는 입력(출처를 모름·제목 없음)이면 null — 카드에 이유를 달지 않는다. */
    public RecReason build(String source, Long seedContentId, String seedTitle) {
        if (source == null || seedContentId == null || seedTitle == null) return null;
        String title = seedTitle.trim();
        if (title.isEmpty()) return null;
        String suffix = SUFFIX_BY_SOURCE.get(source);
        if (suffix == null) return null;
        return new RecReason(source, seedContentId, title + josa(title) + suffix);
    }

    /** 마지막 글자가 한글이면 받침으로 을/를, 아니면 을(를) (REC_TAB_DESIGN §2-3). */
    public static String josa(String title) {
        String trimmed = title.trim();
        char last = trimmed.charAt(trimmed.length() - 1);
        if (last < HANGUL_FIRST || last > HANGUL_LAST) return "을(를)";
        return (last - HANGUL_FIRST) % JONGSEONG_COUNT == 0 ? "를" : "을";
    }
}
