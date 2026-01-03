//package com.example.crawler.util;
//
//import java.awt.AWTException;
//import java.awt.Robot;
//import java.awt.Toolkit;
//import java.awt.datatransfer.StringSelection;
//import java.awt.event.KeyEvent;
//import java.util.Set;
//
//import org.openqa.selenium.By;
//import org.openqa.selenium.Cookie;
//import org.openqa.selenium.JavascriptExecutor;
//import org.openqa.selenium.WebDriver;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.stereotype.Component;
//
//@Component
//public class NaverLoginHandler {
//
//    private final int SLEEP_TIME = 2000;
//
//    @Value("${naver.id}")
//    private String naverId;
//    @Value("${naver.pw}")
//    private String naverPw;
//
//    public void naverLogin(WebDriver driver) throws InterruptedException {
//        naverLogin(driver, naverId, naverPw);
//    }
//
//    public void naverLogin(WebDriver driver, String id, String pw) throws InterruptedException {
//        driver.get("https://nid.naver.com/nidlogin.login?mode=form&url=https://www.naver.com/");
//
//        try {
//            Thread.sleep(SLEEP_TIME);
//        } catch (InterruptedException e) {
//            Thread.currentThread().interrupt(); // 인터럽트 상태 복원
//            throw e; // 상위로 전파
//        }
//
//        JavascriptExecutor js = (JavascriptExecutor) driver;
//        js.executeScript("document.getElementById('id').value='" + id + "'");
//        js.executeScript("document.getElementById('pw').value='" + pw + "'");
//
//        driver.findElement(By.id("log.login")).click();
//    }
//
//    public String getCookieString(WebDriver driver) {
//        Set<Cookie> cookies = driver.manage().getCookies();
//        if (cookies.isEmpty()) {
//            System.out.println("쿠키가 없습니다. 로그인 실패?");
//            return "";
//        }
//        System.out.println("=== Selenium 쿠키 목록 ===");
//        StringBuilder sb = new StringBuilder();
//        for (Cookie c : cookies) {
//            System.out.println("Name: " + c.getName()
//                    + ", Value: " + c.getValue()
//                    + ", Domain: " + c.getDomain()
//                    + ", Path: " + c.getPath()
//                    + ", Expiry: " + c.getExpiry());
//            sb.append(c.getName()).append("=").append(c.getValue()).append("; ");
//        }
//        String cookieString = sb.toString().trim();
//        if (cookieString.endsWith(";")) {
//            cookieString = cookieString.substring(0, cookieString.length() - 1);
//        }
//        System.out.println("\n=== 병합된 쿠키 문자열 ===\n" + cookieString);
//        return cookieString;
//    }
//
//}


