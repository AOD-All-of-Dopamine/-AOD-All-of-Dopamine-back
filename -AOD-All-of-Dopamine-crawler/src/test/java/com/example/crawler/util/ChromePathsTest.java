package com.example.crawler.util;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ChromePathsTest {

    @Test
    void emptyEnvLeavesBothUnsetSoWebDriverManagerDecides() {
        ChromePaths p = ChromePaths.from(Map.of());
        assertNull(p.chromeBinary());
        assertNull(p.chromeDriver());
        assertFalse(p.hasDriver());
    }

    @Test
    void driverPathAloneIsEnough() {
        ChromePaths p = ChromePaths.from(Map.of("CHROMEDRIVER_PATH", "/usr/bin/chromedriver"));
        assertEquals("/usr/bin/chromedriver", p.chromeDriver());
        assertNull(p.chromeBinary());
        assertTrue(p.hasDriver());
    }

    @Test
    void bothPathsAreReadAndTrimmed() {
        ChromePaths p = ChromePaths.from(Map.of(
                "CHROME_BIN", " /usr/bin/chromium ",
                "CHROMEDRIVER_PATH", "/usr/bin/chromedriver\n"));
        assertEquals("/usr/bin/chromium", p.chromeBinary());
        assertEquals("/usr/bin/chromedriver", p.chromeDriver());
    }

    @Test
    void blankValuesCountAsAbsent() {
        // compose 의 ${VAR:-} 처럼 빈 문자열로 들어오는 경우
        ChromePaths p = ChromePaths.from(Map.of("CHROME_BIN", "  ", "CHROMEDRIVER_PATH", ""));
        assertNull(p.chromeBinary());
        assertFalse(p.hasDriver());
    }
}
