package com.example.AOD.game.steam.fetcher;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class SteamApiFetcher {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${steam.api.key:}")
    private String steamApiKey;

    private static final String APP_DETAILS_URL = "https://store.steampowered.com/api/appdetails?appids={appId}&l=korean";

    /**
     * Steam에 등록된 *게임* 앱 목록만 가져옵니다. (IStoreService 사용)
     * @return 게임 앱 정보(appid, name 등) Map의 리스트
     */
    public List<Map<String, Object>> fetchGameApps() {
        String url = "https://api.steampowered.com/IStoreService/GetAppList/v1/?key=" + steamApiKey + "&include_games=true&include_dlc=false&include_software=false&include_videos=false&include_hardware=false";
        try {
            String response = restTemplate.getForObject(url, String.class);
            JsonNode root = objectMapper.readTree(response);
            JsonNode appsNode = root.path("response").path("apps");
            if (appsNode.isArray()) {
                return objectMapper.convertValue(appsNode, new TypeReference<List<Map<String, Object>>>() {});
            }
        } catch (IOException e) {
            log.error("Steam 게임 앱 목록을 가져오는 중 오류 발생: {}", e.getMessage());
        }
        return Collections.emptyList();
    }

    /**
     * 특정 appId의 게임 상세 정보를 Map 형태로 가져옵니다.
     * @param appId Steam 게임의 고유 ID
     * @return 게임 상세 정보 Map 객체
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> fetchGameDetails(Long appId) {
        try {
            Map<String, Object> response = restTemplate.getForObject(APP_DETAILS_URL, Map.class, appId);

            if (response != null && response.containsKey(String.valueOf(appId))) {
                Map<String, Object> appData = (Map<String, Object>) response.get(String.valueOf(appId));
                boolean success = (boolean) appData.getOrDefault("success", false);

                if (success && appData.containsKey("data")) {
                    return (Map<String, Object>) appData.get("data");
                }
            }
        } catch (Exception e) {
            log.warn("AppID {}의 상세 정보를 가져오는 중 오류 발생: {}", appId, e.getMessage());
        }
        return null;
    }
}