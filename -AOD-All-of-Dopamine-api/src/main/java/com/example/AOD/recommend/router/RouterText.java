package com.example.AOD.recommend.router;

/**
 * 라우터가 준 문자열을 우리 저장소에 넣어도 되는지 판정한다.
 *
 * 왜 필요한가: 라우터 응답의 platform·key·candidateSource·factorSchema 는 외부 입력이다.
 * 이 값들은 rec_item_served(text·jsonb)와 rec_chain.skipped_keys(text[])로 그대로 흘러가는데,
 * PostgreSQL 의 text 는 NUL 문자를 받지 못한다 — 한 글자 때문에 로그 배치 200행이 통째로 거부되고
 * 체인 갱신이 실패한다. 길이도 막는다(코퍼스 키는 길어야 수십 자다).
 */
public final class RouterText {

    /** 실제 키는 Steam appid·TMDB id·네이버 titleId 라 20자를 넘지 않는다. 여유를 크게 둔 상한. */
    public static final int MAX_LENGTH = 200;

    private RouterText() { }

    /** 비어 있지 않고, 상한 안이고, 제어 문자가 없으면 쓸 수 있다. */
    public static boolean isSafe(String value) {
        if (value == null || value.isEmpty() || value.length() > MAX_LENGTH) return false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < ' ' || c == 127) return false;
        }
        return true;
    }

    /** 쓸 수 없으면 null — 필수가 아닌 칸(factor_schema 등)에 쓴다. */
    public static String safeOrNull(String value) {
        return isSafe(value) ? value : null;
    }
}
