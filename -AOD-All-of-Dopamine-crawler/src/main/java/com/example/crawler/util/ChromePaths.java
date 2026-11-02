package com.example.crawler.util;

import java.util.Map;

/**
 * 컨테이너가 넘겨 주는 Chrome·chromedriver 경로.
 * 비어 있으면 null — 그때는 예전처럼 WebDriverManager 가 드라이버를 정한다(로컬 개발).
 * arm64 리눅스(맥미니 main-server)에는 공식 chromedriver 배포본이 없어 OS 패키지(chromium-driver) 경로를 받는다.
 */
record ChromePaths(String chromeBinary, String chromeDriver) {

    static final String CHROME_BIN = "CHROME_BIN";
    static final String CHROMEDRIVER_PATH = "CHROMEDRIVER_PATH";

    static ChromePaths from(Map<String, String> env) {
        return new ChromePaths(blankToNull(env.get(CHROME_BIN)), blankToNull(env.get(CHROMEDRIVER_PATH)));
    }

    boolean hasDriver() {
        return chromeDriver != null;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
