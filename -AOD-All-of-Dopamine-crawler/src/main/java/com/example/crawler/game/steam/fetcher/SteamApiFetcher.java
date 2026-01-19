package com.example.crawler.game.steam.fetcher;

import com.example.crawler.game.steam.util.SteamRateLimiter;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
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
    private final SteamRateLimiter rateLimiter;

    @Value("${steam.api.key:}")
    private String steamApiKey;

    private static final String APP_DETAILS_URL = "https://store.steampowered.com/api/appdetails?appids={appId}&l=korean";

    /**
     * Steam에 등록된 *게임* 앱 목록을 페이지네이션으로 안정적으로 가져옵니다. (IStoreService V1 사용)
     * 
     * Steam API는 한 번에 최대 50,000개까지만 반환하므로, 페이지네이션을 통해 전체 목록을 수집합니다.
     * 
     * @return 게임 앱 정보(appid, name 등) Map의 리스트
     */
    public List<Map<String, Object>> fetchGameApps() {
        List<Map<String, Object>> allApps = new java.util.ArrayList<>();
        Integer lastAppId = null;
        int maxRetries = 3;
        int pageCount = 0;
        
        log.info("Steam 게임 앱 목록 수집 시작 (페이지네이션 방식)");
        
        while (true) {
            pageCount++;
            int retryCount = 0;
            boolean success = false;
            
            while (retryCount < maxRetries && !success) {
                try {
                    // Rate Limiter를 통한 요청 제한 준수
                    rateLimiter.acquirePermit();
                    
                    String url = buildGetAppListUrl(lastAppId);
                    String response = restTemplate.getForObject(url, String.class);
                    JsonNode root = objectMapper.readTree(response);
                    JsonNode responseNode = root.path("response");
                    JsonNode appsNode = responseNode.path("apps");
                    
                    if (appsNode.isArray() && appsNode.size() > 0) {
                        List<Map<String, Object>> pageApps = objectMapper.convertValue(appsNode, 
                                new TypeReference<List<Map<String, Object>>>() {});
                        
                        int beforeSize = allApps.size();
                        allApps.addAll(pageApps);
                        int addedCount = allApps.size() - beforeSize;
                        
                        log.info("Steam 앱 목록 페이지 {} 수집 완료: {}개 추가 (전체: {}개)", 
                                pageCount, addedCount, allApps.size());
                        
                        // 마지막 앱의 ID를 다음 요청의 시작점으로 사용
                        Map<String, Object> lastApp = pageApps.get(pageApps.size() - 1);
                        Object appIdObj = lastApp.get("appid");
                        Integer newLastAppId = null;
                        if (appIdObj instanceof Number) {
                            newLastAppId = ((Number) appIdObj).intValue();
                        } else if (appIdObj != null) {
                            try {
                                newLastAppId = Integer.parseInt(appIdObj.toString());
                            } catch (NumberFormatException e) {
                                log.error("appid 파싱 실패: {}", appIdObj);
                            }
                        }
                        
                        success = true;
                        
                        // Steam API의 has_more 필드로 다음 페이지 존재 여부 확인
                        boolean hasMore = responseNode.path("have_more_results").asBoolean(false);
                        
                        if (!hasMore) {
                            log.info("Steam 앱 목록 수집 완료 (have_more_results=false): 총 {}개 ({}페이지)", 
                                    allApps.size(), pageCount);
                            return allApps;
                        }
                        
                        // 중복 방지: lastAppId가 변경되지 않으면 종료
                        if (newLastAppId != null && newLastAppId.equals(lastAppId)) {
                            log.warn("Steam 앱 목록 수집 중단 (lastAppId 중복): 총 {}개 ({}페이지)", 
                                    allApps.size(), pageCount);
                            return allApps;
                        }
                        
                        lastAppId = newLastAppId;
                        
                    } else {
                        // 빈 응답 = 더 이상 데이터 없음
                        log.info("Steam 앱 목록 수집 완료 (빈 응답): 총 {}개 ({}페이지)", allApps.size(), pageCount);
                        return allApps;
                    }
                    
                } catch (HttpClientErrorException.TooManyRequests e) {
                    retryCount++;
                    log.warn("Steam API Rate Limit 초과 (페이지 {}). 재시도 {}/{}. 60초 대기...", 
                            pageCount, retryCount, maxRetries);
                    try {
                        Thread.sleep(60000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        log.error("Steam 앱 목록 수집 중단됨");
                        return allApps;
                    }
                } catch (IOException e) {
                    retryCount++;
                    log.error("Steam 앱 목록 페이지 {} 파싱 오류 (재시도 {}/{}): {}", 
                            pageCount, retryCount, maxRetries, e.getMessage());
                    if (retryCount >= maxRetries) {
                        log.error("Steam 앱 목록 수집 실패. 현재까지 수집된 {}개 반환", allApps.size());
                        return allApps;
                    }
                } catch (Exception e) {
                    retryCount++;
                    log.error("Steam 앱 목록 수집 중 예상치 못한 오류 (재시도 {}/{}): {}", 
                            retryCount, maxRetries, e.getMessage(), e);
                    if (retryCount >= maxRetries) {
                        log.error("Steam 앱 목록 수집 실패. 현재까지 수집된 {}개 반환", allApps.size());
                        return allApps;
                    }
                }
            }
            
            // 재시도 횟수 초과 시
            if (!success) {
                log.error("Steam 앱 목록 페이지 {} 수집 실패 (재시도 초과). 현재까지 수집된 {}개 반환", 
                        pageCount, allApps.size());
                return allApps;
            }
        }
    }
    
    /**
     * IStoreService GetAppList API URL 생성
     * 
     * @param lastAppId 마지막으로 받은 appId (페이지네이션용)
     * @return 완전한 API URL
     */
    private String buildGetAppListUrl(Integer lastAppId) {
        StringBuilder url = new StringBuilder("https://api.steampowered.com/IStoreService/GetAppList/v1/");
        url.append("?key=").append(steamApiKey);
        url.append("&include_games=true");
        url.append("&include_dlc=false");
        url.append("&include_software=false");
        url.append("&include_videos=false");
        url.append("&include_hardware=false");
        url.append("&max_results=50000"); // API 최대값
        
        if (lastAppId != null) {
            url.append("&last_appid=").append(lastAppId);
        }
        
        return url.toString();
    }

    /**
     * 특정 appId의 게임 상세 정보를 Map 형태로 가져옵니다.
     * 
     * @param appId Steam 게임의 고유 ID
     * @return 게임 상세 정보 Map 객체
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> fetchGameDetails(Long appId) {
        int maxRetries = 3;
        int retryCount = 0;

        while (retryCount <= maxRetries) {
            try {
                // Rate Limiter를 통한 요청 제한 준수
                rateLimiter.acquirePermit();
                
                Map<String, Object> response = restTemplate.getForObject(APP_DETAILS_URL, Map.class, appId);

                if (response != null && response.containsKey(String.valueOf(appId))) {
                    Map<String, Object> appData = (Map<String, Object>) response.get(String.valueOf(appId));
                    boolean success = (boolean) appData.getOrDefault("success", false);

                    if (success && appData.containsKey("data")) {
                        return (Map<String, Object>) appData.get("data");
                    }
                }
                return null; // 성공했지만 데이터가 없거나 success=false인 경우

            } catch (HttpClientErrorException.TooManyRequests e) {
                retryCount++;
                log.warn("Steam API Rate Limit exceeded for AppID {}. Retry {}/{}. Waiting 60 seconds...", appId,
                        retryCount, maxRetries);
                try {
                    Thread.sleep(60000); // 60초 대기
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            } catch (Exception e) {
                log.warn("AppID {}의 상세 정보를 가져오는 중 오류 발생: {}", appId, e.getMessage());
                return null;
            }
        }
        log.error("Steam API Rate Limit 재시도 횟수 초과. AppID: {}", appId);
        return null;
    }
}

