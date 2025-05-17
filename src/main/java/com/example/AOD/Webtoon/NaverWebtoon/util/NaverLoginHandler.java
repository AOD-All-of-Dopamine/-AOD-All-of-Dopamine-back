package com.example.AOD.Webtoon.NaverWebtoon.util;

import java.awt.AWTException;
import java.util.Set;

import org.openqa.selenium.Cookie;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class NaverLoginHandler {

    private final int MANUAL_LOGIN_WAIT_TIME = 10000; // 30초 대기 시간

    /**
     * 네이버 로그인 페이지를 열고 사용자가 수동으로 로그인할 수 있도록 대기합니다.
     * id와 pw 파라미터는 사용하지 않지만 기존 메서드 시그니처와의 호환성을 위해 유지합니다.
     */
    public void naverLogin(WebDriver driver, String id, String pw) throws InterruptedException, AWTException {
        // 네이버 로그인 페이지로 이동
        driver.get("https://nid.naver.com/nidlogin.login?mode=form&url=https://www.naver.com/");

        // 수동 로그인 안내 메시지
        System.out.println("=====================================================");
        System.out.println("네이버 로그인 페이지가 열렸습니다.");
        System.out.println("브라우저 창에서 수동으로 로그인해 주세요.");
        System.out.println("로그인을 완료하면 자동으로 다음 단계로 진행됩니다.");
        System.out.println("최대 " + (MANUAL_LOGIN_WAIT_TIME/1000) + "초 동안 대기합니다.");
        System.out.println("=====================================================");

        // 수동 로그인을 위한 대기 시간
        Thread.sleep(MANUAL_LOGIN_WAIT_TIME);

        // 로그인 성공 여부 확인 (Optional)
        if (isLoggedIn(driver)) {
            System.out.println("로그인이 확인되었습니다. 크롤링을 계속 진행합니다.");
        } else {
            System.out.println("로그인이 확인되지 않았습니다. 크롤링은 계속 진행하지만 데이터가 제한될 수 있습니다.");
        }
    }

    /**
     * 로그인 상태인지 확인합니다.
     * 네이버 로그인 시 설정되는 특정 쿠키가 있는지 확인합니다.
     */
    private boolean isLoggedIn(WebDriver driver) {
        Set<Cookie> cookies = driver.manage().getCookies();
        for (Cookie cookie : cookies) {
            // NID_AUT, NID_SES 등의 쿠키가 있으면 로그인된 상태로 간주
            if (cookie.getName().equals("NID_AUT") || cookie.getName().equals("NID_SES")) {
                return true;
            }
        }
        return false;
    }

    /**
     * 드라이버에서 모든 쿠키를 가져와 문자열로 반환합니다.
     */
    public String getCookieString(WebDriver driver) {
        Set<Cookie> cookies = driver.manage().getCookies();
        if (cookies.isEmpty()) {
            System.out.println("쿠키가 없습니다. 로그인 실패?");
            return "";
        }
        System.out.println("=== Selenium 쿠키 목록 ===");
        StringBuilder sb = new StringBuilder();
        for (Cookie c : cookies) {
            System.out.println("Name: " + c.getName()
                    + ", Value: " + c.getValue()
                    + ", Domain: " + c.getDomain()
                    + ", Path: " + c.getPath()
                    + ", Expiry: " + c.getExpiry());
            sb.append(c.getName()).append("=").append(c.getValue()).append("; ");
        }
        String cookieString = sb.toString().trim();
        if (cookieString.endsWith(";")) {
            cookieString = cookieString.substring(0, cookieString.length() - 1);
        }
        System.out.println("\n=== 병합된 쿠키 문자열 ===\n" + cookieString);
        return cookieString;
    }

    /**
     * 로그인 후 특정 페이지(예: 네이버 메인)로 이동합니다.
     * 로그인 상태를 더 확실히 확인하기 위해 사용할 수 있습니다.
     */
    public void navigateToMainPage(WebDriver driver) {
        driver.get("https://www.naver.com/");
        System.out.println("네이버 메인 페이지로 이동했습니다.");
    }
}