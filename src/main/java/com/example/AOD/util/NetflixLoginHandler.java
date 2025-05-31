package com.example.AOD.util;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class NetflixLoginHandler {

    private final int SLEEP_TIME = 2000;

    @Value("${netflix.id}")
    private String netflixId;
    @Value("${netflix.pw}")
    private String netflixPw;
    @Value("${netflix.profileName:#{null}}")
    private String netflixProfileName;

    public void netflixLogin(WebDriver driver) throws InterruptedException {
        netflixLogin(driver, netflixId, netflixPw);
        selectProfile(driver);
    }

    public void netflixLogin(WebDriver driver, String id, String pw) throws InterruptedException {
        driver.get("https://www.netflix.com/login");
        Thread.sleep(SLEEP_TIME);
        if (isLoggedIn(driver)) return;

        WebElement emailField = driver.findElement(By.name("userLoginId"));
        emailField.clear();
        emailField.sendKeys(id);

        WebElement passwordField = driver.findElement(By.name("password"));
        passwordField.clear();
        passwordField.sendKeys(pw);

        passwordField.sendKeys(Keys.ENTER);
        Thread.sleep(SLEEP_TIME*2);
        log.debug("로그인 성공");
    }

    public boolean isLoggedIn(WebDriver driver) {
        return driver.getCurrentUrl().contains("profile") || driver.getCurrentUrl().contains("browse");
    }

    public void selectProfile(WebDriver driver) throws InterruptedException {
        if(isProfilePicked(driver)) return;
        WebElement profileListElem = driver.findElement(By.className("choose-profile"));
        List<WebElement> profileElems = profileListElem.findElements(By.tagName("li"));

        List<Map<String, String>> profileList = getProfileList(profileElems);
        if(profileList.isEmpty()) {
            System.out.println("No profile selected");
            return;
        }
        Map<String, String> selectedProfile = pickProfileFromProfileList(profileList);
        driver.get(selectedProfile.get("url"));
        Thread.sleep(SLEEP_TIME*2);

    }

    public boolean isProfilePicked(WebDriver driver) {
        try {
            driver.findElement(By.className("choose-profile"));
            return false;
        } catch (Exception e) {
            return true;
        }
    }

    private List<Map<String, String>> getProfileList(List<WebElement> profiles) {
        List<Map<String, String>> profileData = new ArrayList<>();
        for (WebElement profile : profiles) {
            // 프로필 추가 버튼은 건너뛰기
            if (!profile.findElements(By.cssSelector("[data-uia='profile-choices-create-button']")).isEmpty()) {
                continue;
            }

            // 프로필 링크와 이름 추출
            WebElement profileLink = profile.findElement(By.className("profile-link"));
            WebElement profileNameElement = profile.findElement(By.className("profile-name"));

            String name = profileNameElement.getText().trim();
            String url = profileLink.getAttribute("href");

            Map<String, String> data = new HashMap<>();
            data.put("name", name);
            data.put("url", url);
            profileData.add(data);
        }
        return profileData;
    }

    private Map<String, String> pickProfileFromProfileList(List<Map<String, String>> profileList) {
        if(netflixProfileName==null || netflixProfileName.trim().isEmpty()) {
            return profileList.get(0);
        }
        for (Map<String, String> profile : profileList) {
            if (netflixProfileName.trim().equals(profile.get("name"))) {
                return profile;
            }
        }
        return profileList.get(0);
    }

}
