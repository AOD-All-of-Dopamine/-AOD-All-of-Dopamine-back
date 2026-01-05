package com.example.crawler.contents.TMDB.processor;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;

@Component
public class TmdbPayloadProcessor {

    private static final String TMDB_IMAGE_BASE_URL = "https://image.tmdb.org/t/p/w500";

    /**
     * TMDB API의 원본 응답 페이로드를 받아 필요한 데이터만 추출하고 정제하여 새로운 Map으로 반환합니다.
     * 
     * 주의: CollectorService에서 platformSpecificId 기반 중복 감지를 사용하므로
     * 동적 필드(vote_count, popularity 등)를 제거하지 않습니다.
     * 이렇게 하면 데이터 갱신 시 최신 정보가 자동으로 반영됩니다.
     * 
     * @param rawPayload TMDB API 원본 응답
     * @return 정제된 데이터 맵
     */
    public Map<String, Object> process(Map<String, Object> rawPayload) {
        Map<String, Object> processedPayload = new HashMap<>();

        // 1. 기본 정보 직접 복사 (동적 필드 포함)
        copyFields(rawPayload, processedPayload);

        // 2. 포스터 이미지 URL 절대 경로로 변환
        convertPosterPathToUrl(rawPayload, processedPayload);

        // 3. 장르(genres) 이름만 추출
        extractGenreNames(rawPayload, processedPayload);

        // 4. 출연진(cast) 및 제작진(crew) 이름 추출
        extractCredits(rawPayload, processedPayload);

        // 5. 시청 가능 OTT(watch providers) 이름 추출
        extractWatchProviders(rawPayload, processedPayload);

        // 동적 필드(popularity, vote_count 등)는 제거하지 않음!
        // platformSpecificId 기반 중복 감지를 사용하므로 모든 데이터를 유지하여
        // 데이터 변경 시 자동 갱신이 가능하도록 함

        return processedPayload;
    }

    private void copyFields(Map<String, Object> source, Map<String, Object> dest) {
        String[] fieldsToCopy = {
                "id", "overview", "release_date", "first_air_date",
                "name", "title", "original_name", "original_title",
                "runtime", "episode_run_time", "number_of_seasons"
        };
        for (String field : fieldsToCopy) {
            if (source.containsKey(field) && source.get(field) != null) {
                dest.put(field, source.get(field));
            }
        }
    }

    private void convertPosterPathToUrl(Map<String, Object> source, Map<String, Object> dest) {
        if (source.containsKey("poster_path") && source.get("poster_path") != null) {
            String posterPath = source.get("poster_path").toString();
            dest.put("poster_image_url", TMDB_IMAGE_BASE_URL + posterPath);
        }
    }

    @SuppressWarnings("unchecked")
    private void extractGenreNames(Map<String, Object> source, Map<String, Object> dest) {
        Object genresObject = source.get("genres");
        if (genresObject instanceof List) {
            List<Map<String, Object>> genreList = (List<Map<String, Object>>) genresObject;
            List<String> genreNames = genreList.stream()
                    .map(item -> item.get("name"))
                    .filter(name -> name != null)
                    .map(Object::toString)
                    .collect(Collectors.toList());
            dest.put("genres", genreNames);
        }
    }

    @SuppressWarnings("unchecked")
    private void extractCredits(Map<String, Object> source, Map<String, Object> dest) {
        Object creditsObject = source.get("credits");
        if (!(creditsObject instanceof Map)) return;

        Map<String, Object> creditsMap = (Map<String, Object>) creditsObject;

        // 출연진 (상위 10명)
        Object castObject = creditsMap.get("cast");
        if (castObject instanceof List) {
            List<Map<String, Object>> castList = (List<Map<String, Object>>) castObject;
            List<String> castNames = castList.stream()
                    .limit(10)
                    .map(c -> c.get("name"))
                    .filter(name -> name != null)
                    .map(Object::toString)
                    .collect(Collectors.toList());
            dest.put("cast", castNames);
        }

        // 제작진 (감독, 작가)
        Object crewObject = creditsMap.get("crew");
        if (crewObject instanceof List) {
            List<Map<String, Object>> crewList = (List<Map<String, Object>>) crewObject;
            List<String> directors = filterCrewByJob(crewList, "Director");
            if (!directors.isEmpty()) {
                dest.put("directors", directors);
            }

            List<String> writers = filterCrewByJob(crewList, "Writer");
            if (!writers.isEmpty()) {
                dest.put("writers", writers);
            }
        }
    }

    private List<String> filterCrewByJob(List<Map<String, Object>> crewList, String job) {
        return crewList.stream()
                .filter(c -> job.equals(c.get("job")))
                .map(c -> c.get("name"))
                .filter(name -> name != null)
                .map(Object::toString)
                .collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    private void extractWatchProviders(Map<String, Object> source, Map<String, Object> dest) {
        Object providersObject = source.get("watch/providers");
        if (!(providersObject instanceof Map)) return;

        Object resultsObject = ((Map<String, Object>) providersObject).get("results");
        if (!(resultsObject instanceof Map)) return;

        Object krObject = ((Map<String, Object>) resultsObject).get("KR");
        if (!(krObject instanceof Map)) return;

        Object flatrateObject = ((Map<String, Object>) krObject).get("flatrate");
        if (flatrateObject instanceof List) {
            List<Map<String, Object>> flatrateList = (List<Map<String, Object>>) flatrateObject;
            List<String> providerNames = flatrateList.stream()
                    .map(p -> p.get("provider_name"))
                    .filter(name -> name != null)
                    .map(Object::toString)
                    .collect(Collectors.toList());

            if (!providerNames.isEmpty()) {
                dest.put("watch_providers", providerNames);
            }
        }
    }
}


