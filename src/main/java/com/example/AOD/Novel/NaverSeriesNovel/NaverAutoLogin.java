package com.example.AOD.Novel.NaverSeriesNovel;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.Cookie;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.time.Duration;
import java.util.Set;

public class NaverAutoLogin {

    private static final String NAVER_ID = "";       // 자신의 아이디
    private static final String NAVER_PW = ""; // 자신의 비밀번호

    public static void main(String[] args) {
        String cookieString = loginAndGetCookies();
        System.out.println("\n--- 추출된 쿠키 문자열 ---");
        System.out.println(cookieString);
    }

    /**
     * Selenium으로 네이버 로그인 후, 쿠키를 Jsoup에서 쓸 수 있도록
     * "key=value; key2=value2..." 형태의 쿠키 문자열로 반환
     */
    public static String loginAndGetCookies() {
        System.setProperty("webdriver.chrome.driver", "C:\\Users\\kokyungwoo\\Desktop\\chromedriver-win64\\chromedriver.exe");

        ChromeOptions options = new ChromeOptions();
        // 브라우저 확인하려면 주석 처리
        //options.addArguments("--headless");
        WebDriver driver = new ChromeDriver(options);

        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(20));
        Actions actions = new Actions(driver);

        try {
            // 1) 로그인 페이지 접속
            driver.get("https://nid.naver.com/nidlogin.login");
            wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("#id")));
            wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("#pw")));

            // 2) 아이디, 비번 입력
            WebElement idField = driver.findElement(By.cssSelector("#id"));
            WebElement pwField = driver.findElement(By.cssSelector("#pw"));
            pasteText(actions, idField, "");
            pasteText(actions, pwField, "");

            // 3) 로그인 버튼 클릭
            WebElement loginBtn = driver.findElement(By.id("log.login"));
            wait.until(ExpectedConditions.elementToBeClickable(loginBtn));
            loginBtn.click();


            // 4) 로그인 직후 페이지 -> 네이버 메인(또는 시리즈 메인)으로 직접 이동
            //    (로그인 직후 페이지 구조가 달라서 gnb_my_layer가 없을 수 있으므로)
            driver.get("https://www.naver.com/");
            // 네이버 메인에 #gnb_my_layer가 존재하는지 확인
            System.out.println("네이버 메인 페이지로 이동. 로그인 성공!");

            // 5) 모든 쿠키 출력 + 병합
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

        } catch (Exception e) {
            e.printStackTrace();
            return "";
        } finally {
            driver.quit();
        }
    }

    private static void pasteText(Actions actions, WebElement element, String text) throws Exception {
        StringSelection selection = new StringSelection(text);
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, null);

        element.click();
        actions.keyDown(org.openqa.selenium.Keys.CONTROL)
                .sendKeys("v")
                .keyUp(org.openqa.selenium.Keys.CONTROL)
                .perform();
        Thread.sleep(500);
    }
}