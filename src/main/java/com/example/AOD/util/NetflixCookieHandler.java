package com.example.AOD.util;

//import io.netty.handler.codec.http.cookie.Cookie;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.Cookie;
import org.openqa.selenium.WebDriver;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class NetflixCookieHandler {

    private String cookieFilePath = "netflix_cookie.json";

    public void saveCookies(WebDriver driver) {
        try {
            Set<Cookie> cookies = driver.manage().getCookies();
            Gson gson = new GsonBuilder().setPrettyPrinting().create();

            List<Map<String, Object>> cookieList = new ArrayList<>();
            for (Cookie cookie : cookies) {
                Map<String, Object> cookieMap = new HashMap<>();
                cookieMap.put("name", cookie.getName());
                cookieMap.put("value", cookie.getValue());
                cookieMap.put("domain", cookie.getDomain());
                cookieMap.put("path", cookie.getPath());

                // 만료 시간이 있는 경우
                if (cookie.getExpiry() != null) {
                    cookieMap.put("expiry", cookie.getExpiry().getTime());
                }

                cookieMap.put("secure", cookie.isSecure());
                cookieMap.put("httpOnly", cookie.isHttpOnly());

                cookieList.add(cookieMap);
            }

            String json = gson.toJson(cookieList);
            FileWriter writer = new FileWriter(cookieFilePath);
            writer.write(json);
            writer.close();
            log.debug("cookie save complete");
        } catch (Exception e) {
            log.debug("error in save cookie");
        }
    }

    public void loadCookies(WebDriver driver) {
        try {
            Gson gson = new Gson();
            File cookieFile = new File(cookieFilePath);
            if (!cookieFile.exists()) {
                log.debug("cookie file does not exist");
                return;
            }

            driver.get("https://www.netflix.com");
            Thread.sleep(1000);

            String json = new String(Files.readAllBytes(cookieFile.toPath()));
            List<Map<String, Object>> cookieList = gson.fromJson(json, new TypeToken<List<Map<String, Object>>>(){}.getType());

            if (cookieList == null || cookieList.isEmpty()) {
                log.debug("cookie file does not exist");
                return;
            }

            for (Map<String, Object> cookieMap : cookieList) {
                String name = (String) cookieMap.get("name");
                String value = (String) cookieMap.get("value");
                String domain = (String) cookieMap.get("domain");
                String path = (String) cookieMap.get("path");

                // null 체크
                if (name == null || value == null) {
                    continue;
                }

                Cookie.Builder cookieBuilder = new Cookie.Builder(name, value);

                if (domain != null) {
                    cookieBuilder.domain(domain);
                }

                if (path != null) {
                    cookieBuilder.path(path);
                }

                // 만료 시간이 있는 경우
                if (cookieMap.containsKey("expiry")) {
                    Date expiry = new Date(((Number) cookieMap.get("expiry")).longValue());
                    cookieBuilder.expiresOn(expiry);
                }

                if (cookieMap.containsKey("secure") && (Boolean) cookieMap.get("secure")) {
                    cookieBuilder.isSecure(true);
                }

                if (cookieMap.containsKey("httpOnly") && (Boolean) cookieMap.get("httpOnly")) {
                    cookieBuilder.isHttpOnly(true);
                }

                Cookie cookie = cookieBuilder.build();
                driver.manage().addCookie(cookie);
            }
            log.debug("cookie load complete");
        } catch (Exception e) {
            log.debug("error in load cookie");
        }
    }

}
