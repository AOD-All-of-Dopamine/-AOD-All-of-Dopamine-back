package com.example.AOD.naverWebtoonCrawler.util;

import java.awt.AWTException;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.KeyEvent;
import org.openqa.selenium.By;
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
