package com.example.AOD.Webtoon.NaverWebtoon.util;

import java.awt.AWTException;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.KeyEvent;
import java.util.Set;

import org.openqa.selenium.By;
import org.openqa.selenium.Cookie;
import org.openqa.selenium.WebDriver;
import org.springframework.stereotype.Component;

@Component
public class NaverLoginHandler {

    private final int SLEEP_TIME = 2000;

    public void naverLogin(WebDriver driver, String id, String pw) throws InterruptedException, AWTException {
        driver.get("https://nid.naver.com/nidlogin.login?mode=form&url=https://www.naver.com/");
        Thread.sleep(SLEEP_TIME);

        driver.findElement(By.className("input_id")).click();
        pasteString(id);
        Thread.sleep(SLEEP_TIME/4);

        driver.findElement(By.className("input_pw")).click();
        pasteString(pw);

        driver.findElement(By.id("log.login")).click();
        Thread.sleep(SLEEP_TIME);

    }

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

    public static void pasteString(String str) throws AWTException {
        //copy
        StringSelection selection = new StringSelection(str);
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, null);

        //paste
        Robot robot = new Robot();
        robot.keyPress(KeyEvent.VK_CONTROL);
        robot.keyPress(KeyEvent.VK_V);
        robot.keyRelease(KeyEvent.VK_V);
        robot.keyRelease(KeyEvent.VK_CONTROL);
    }

}
