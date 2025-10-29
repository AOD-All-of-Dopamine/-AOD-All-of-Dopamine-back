package com.example.AOD.game.steam.processor;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;

@Component
public class SteamPayloadProcessor {

    /**
     * Steam API의 원본 응답 페이로드를 받아 필요한 데이터만 추출하고 정제하여 새로운 Map으로 반환합니다.
     * @param rawPayload Steam API 원본 응답
     * @return 정제된 데이터 맵
     */
    public Map<String, Object> process(Map<String, Object> rawPayload) {
        if (rawPayload == null) {
            return new HashMap<>();
        }
        // 원본 수정을 피하기 위해 변경 가능한 복사본 생성
        Map<String, Object> processedPayload = new HashMap<>(rawPayload);

        // 장르(genres)의 description 값만 추출
        extractGenreDescriptions(processedPayload);

        // 카테고리(categories)의 description 값만 추출
        extractCategoryDescriptions(processedPayload);

        return processedPayload;
    }

    @SuppressWarnings("unchecked")
    private void extractGenreDescriptions(Map<String, Object> payload) {
        Object genresObject = payload.get("genres");
        if (genresObject instanceof List) {
            List<Map<String, Object>> genreList = (List<Map<String, Object>>) genresObject;
            List<String> genreDescriptions = genreList.stream()
                    .map(item -> item.get("description"))
                    .filter(description -> description != null)
                    .map(Object::toString)
                    .collect(Collectors.toList());
            // 기존 genres 리스트를 description 문자열 리스트로 덮어쓰기
            payload.put("genres", genreDescriptions);
        }
    }

    @SuppressWarnings("unchecked")
    private void extractCategoryDescriptions(Map<String, Object> payload) {
        Object categoriesObject = payload.get("categories");
        if (categoriesObject instanceof List) {
            List<Map<String, Object>> categoryList = (List<Map<String, Object>>) categoriesObject;
            List<String> categoryDescriptions = categoryList.stream()
                    .map(item -> item.get("description"))
                    .filter(description -> description != null)
                    .map(Object::toString)
                    .collect(Collectors.toList());
            payload.put("categories", categoryDescriptions);
        }
    }
}
