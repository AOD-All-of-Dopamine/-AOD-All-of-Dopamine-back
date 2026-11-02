package com.example.crawler.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.chrome.ChromeOptions;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ChromeDriverProviderTest {

    @AfterEach
    void clearDriverProperty() {
        System.clearProperty("webdriver.chrome.driver");
    }

    @Test
    void setupUsesGivenDriverPathInsteadOfDownloading() {
        new ChromeDriverProvider(new ChromePaths(null, "/usr/bin/chromedriver")).setupChromeDriver();

        assertEquals("/usr/bin/chromedriver", System.getProperty("webdriver.chrome.driver"));
    }

    @Test
    void optionsUseGivenChromeBinaryAndNewHeadless() {
        ChromeOptions options = new ChromeDriverProvider(
                new ChromePaths("/usr/bin/chromium", "/usr/bin/chromedriver")).buildOptions();

        Map<String, Object> chrome = chromeOptions(options);
        assertEquals("/usr/bin/chromium", chrome.get("binary"));
        List<?> args = (List<?>) chrome.get("args");
        assertTrue(args.contains("--headless=new"), args.toString());
        assertTrue(args.contains("--no-sandbox"), args.toString());
    }

    @Test
    void optionsLeaveBinaryUnsetWhenNotGiven() {
        ChromeOptions options = new ChromeDriverProvider(new ChromePaths(null, null)).buildOptions();

        assertFalse(chromeOptions(options).containsKey("binary"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> chromeOptions(ChromeOptions options) {
        return (Map<String, Object>) options.asMap().get(ChromeOptions.CAPABILITY);
    }
}
