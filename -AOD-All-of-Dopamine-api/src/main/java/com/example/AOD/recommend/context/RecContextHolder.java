package com.example.AOD.recommend.context;

/** 요청 스레드의 RecContext. RecContextFilter 가 채우고 비운다. 필터 밖(배치·테스트)에서는 EMPTY. */
public final class RecContextHolder {

    private static final ThreadLocal<RecContext> CURRENT = new ThreadLocal<>();

    private RecContextHolder() { }

    public static RecContext current() {
        RecContext ctx = CURRENT.get();
        return ctx != null ? ctx : RecContext.EMPTY;
    }

    public static void set(RecContext ctx) {
        CURRENT.set(ctx);
    }

    public static void clear() {
        CURRENT.remove();
    }
}
