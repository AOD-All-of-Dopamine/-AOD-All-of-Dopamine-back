package com.example.crawler.util;

import io.github.bonigarcia.wdm.WebDriverManager;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Slf4j
@Component
public class ChromeDriverProvider {

    private final ChromePaths paths;

    public ChromeDriverProvider() {
        this(ChromePaths.from(System.getenv()));
    }

    ChromeDriverProvider(ChromePaths paths) {
        this.paths = paths;
    }

    /**
     * 🚀 앱 시작 시 1회만 ChromeDriver 를 정한다.
     * - 매번 setup() 호출 시 HTTP 클라이언트 스레드 누수 발생 (75분간 462개 생성 문제 해결)
     * - CHROMEDRIVER_PATH 가 있으면(컨테이너) 그 경로를 쓰고 다운로드를 건너뛴다.
     */
    @PostConstruct
    public void setupChromeDriver() {
        if (paths.hasDriver()) {
            System.setProperty("webdriver.chrome.driver", paths.chromeDriver());
            log.info("✅ ChromeDriver 경로 지정: {} (Chrome: {})", paths.chromeDriver(),
                    paths.chromeBinary() == null ? "기본 위치" : paths.chromeBinary());
            return;
        }
        log.info("🔧 ChromeDriver 초기 설정 시작 (WebDriverManager, 1회만 실행)");
        WebDriverManager.chromedriver().setup();
        log.info("✅ ChromeDriver 설정 완료");
    }

    public WebDriver getDriver() {
        // setup 은 @PostConstruct 에서 1회만 — 여기서 다시 부르지 않는다
        ChromeDriver driver = new ChromeDriver(buildOptions());

        // 🚀 핵심: Timeout 설정 (좀비 프로세스 방지!)
        // timeout 없이 driver.get() 호출 시 무한 대기 → quit() 도달 못 함 → 좀비 프로세스
        driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(30));
        driver.manage().timeouts().scriptTimeout(Duration.ofSeconds(30));
        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));

        log.debug("ChromeDriver 생성 완료 (timeout 설정: pageLoad=30s, script=30s, implicit=10s)");
        return driver;
    }

    ChromeOptions buildOptions() {
        ChromeOptions options = new ChromeOptions();
        if (paths.chromeBinary() != null) {
            options.setBinary(paths.chromeBinary());
        }

        // 기본 옵션
        options.addArguments("--headless=new");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--mute-audio");
        options.addArguments("--no-zygote");  // 🚀 Chrome zygote 프로세스 생성 방지 (좀비 방지)

        // 🚀 메모리 최적화 옵션
        options.addArguments("--disable-gpu");
        options.addArguments("--disable-extensions");
        options.addArguments("--disable-images");
        options.addArguments("--blink-settings=imagesEnabled=false");
        options.addArguments("--disable-plugins");
        options.addArguments("--disable-software-rasterizer");

        options.setExperimentalOption("excludeSwitches", List.of("enable-automation"));
        options.setExperimentalOption("useAutomationExtension", false);
        return options;
    }
}
