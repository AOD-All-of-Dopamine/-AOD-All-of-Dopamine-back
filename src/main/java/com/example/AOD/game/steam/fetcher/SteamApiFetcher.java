package com.example.AOD.game.steam.fetcher;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class SteamApiFetcher {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String ALL_APPS_URL = "https://api.steampowered.com/ISteamApps/GetAppList/v2";
    private static final String APP_DETAILS_URL = "https://store.steampowered.com/api/appdetails?appids={appId}&l=korean";

    /**
     * Steam에 등록된 모든 앱의 목록(appid, name)을 가져옵니다.
     * @return 앱 정보 Map의 리스트
     */
    public List<Map<String, Object>> fetchAllApps() {
        try {
            Map<String, Map<String, List<Map<String, Object>>>> response = restTemplate.getForObject(ALL_APPS_URL, Map.class);
            if (response != null && response.containsKey("applist") && response.get("applist").containsKey("apps")) {
                return response.get("applist").get("apps");
            }
        } catch (Exception e) {
            log.error("Steam 앱 목록을 가져오는 중 오류 발생: {}", e.getMessage());
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
            // RestTemplate은 제네릭 타입의 Map으로 바로 변환 가능합니다.
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