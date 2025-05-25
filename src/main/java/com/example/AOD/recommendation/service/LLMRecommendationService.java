package com.example.AOD.recommendation.service;

import com.example.AOD.recommendation.domain.LLMRecommendationRequest;
import com.example.AOD.recommendation.repository.LLMRecommendationRequestRepository;
import com.example.AOD.common.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class LLMRecommendationService {

    @Autowired
    private LLMRecommendationRequestRepository llmRequestRepository;

    @Autowired
    private MovieCommonRepository movieRepository;

    @Autowired
    private NovelCommonRepository novelRepository;

    @Autowired
    private WebtoonCommonRepository webtoonRepository;

    @Autowired
    private OTTCommonRepository ottRepository;

    @Autowired
    private GameCommonRepository gameRepository;

    @Value("${openai.api.key:test-key}")
    private String openaiApiKey;

    public Map<String, Object> getLLMRecommendations(String username, String userPrompt) {
        try {
            // 1. 현재 사용 가능한 콘텐츠 정보 수집
            String availableContent = buildAvailableContentContext();

            // 2. ChatGPT API 호출
            String systemPrompt = buildSystemPrompt(availableContent);
            String llmResponse = callChatGPTAPI(systemPrompt, userPrompt);

            // 3. 요청과 응답 저장
            LLMRecommendationRequest request = new LLMRecommendationRequest();
            request.setUsername(username);
            request.setUserPrompt(userPrompt);
            request.setLlmResponse(llmResponse);
            llmRequestRepository.save(request);

            // 4. 응답 파싱하여 실제 콘텐츠 정보와 매칭
            Map<String, Object> result = parseAndMatchContent(llmResponse);
            result.put("llmResponse", llmResponse);

            return result;

        } catch (Exception e) {
            e.printStackTrace();
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("error", "추천을 생성하는 중 오류가 발생했습니다: " + e.getMessage());
            return errorResult;
        }
    }

    private String buildAvailableContentContext() {
        StringBuilder context = new StringBuilder();

        // 각 카테고리별로 샘플 콘텐츠 정보 추가 (전체가 아닌 샘플만)
        context.append("사용 가능한 콘텐츠:\n\n");

        try {
            // 영화 정보
            List<Object> movies = movieRepository.findAll().stream().limit(20).collect(Collectors.toList());
            context.append("영화:\n");
            movies.forEach(movie -> {
                try {
                    java.lang.reflect.Field titleField = movie.getClass().getDeclaredField("title");
                    titleField.setAccessible(true);
                    String title = (String) titleField.get(movie);
                    if (title != null) {
                        context.append("- ").append(title).append("\n");
                    }
                } catch (Exception e) {
                    // 무시
                }
            });

            // 웹툰 정보
            List<Object> webtoons = webtoonRepository.findAll().stream().limit(20).collect(Collectors.toList());
            context.append("\n웹툰:\n");
            webtoons.forEach(webtoon -> {
                try {
                    java.lang.reflect.Field titleField = webtoon.getClass().getDeclaredField("title");
                    titleField.setAccessible(true);
                    String title = (String) titleField.get(webtoon);
                    if (title != null) {
                        context.append("- ").append(title).append("\n");
                    }
                } catch (Exception e) {
                    // 무시
                }
            });

            // 웹소설 정보
            List<Object> novels = novelRepository.findAll().stream().limit(20).collect(Collectors.toList());
            context.append("\n웹소설:\n");
            novels.forEach(novel -> {
                try {
                    java.lang.reflect.Field titleField = novel.getClass().getDeclaredField("title");
                    titleField.setAccessible(true);
                    String title = (String) titleField.get(novel);
                    if (title != null) {
                        context.append("- ").append(title).append("\n");
                    }
                } catch (Exception e) {
                    // 무시
                }
            });

            // OTT 콘텐츠 정보
            List<Object> ottContents = ottRepository.findAll().stream().limit(20).collect(Collectors.toList());
            context.append("\nOTT 콘텐츠:\n");
            ottContents.forEach(ott -> {
                try {
                    java.lang.reflect.Field titleField = ott.getClass().getDeclaredField("title");
                    titleField.setAccessible(true);
                    String title = (String) titleField.get(ott);
                    if (title != null) {
                        context.append("- ").append(title).append("\n");
                    }
                } catch (Exception e) {
                    // 무시
                }
            });

            // 게임 정보
            List<Object> games = gameRepository.findAll().stream().limit(20).collect(Collectors.toList());
            context.append("\n게임:\n");
            games.forEach(game -> {
                try {
                    java.lang.reflect.Field titleField = game.getClass().getDeclaredField("title");
                    titleField.setAccessible(true);
                    String title = (String) titleField.get(game);
                    if (title != null) {
                        context.append("- ").append(title).append("\n");
                    }
                } catch (Exception e) {
                    // 무시
                }
            });

        } catch (Exception e) {
            context.append("콘텐츠 정보를 불러오는 중 오류가 발생했습니다.\n");
        }

        return context.toString();
    }

    private String buildSystemPrompt(String availableContent) {
        return """
            당신은 콘텐츠 추천 전문가입니다. 
            사용자의 요청에 따라 영화, 웹툰, 웹소설, OTT 콘텐츠, 게임 등을 추천해주세요.
            
            다음은 현재 사용 가능한 콘텐츠 목록입니다:
            
            """ + availableContent + """
            
            추천할 때는 다음 형식으로 응답해주세요:
            1. 간단한 인사와 추천 이유
            2. 추천 콘텐츠 목록 (카테고리별로 구분)
            3. 각 추천에 대한 간단한 설명
            
            사용자가 원하는 장르, 분위기, 상황 등을 고려하여 개인화된 추천을 제공해주세요.
            """;
    }

    private String callChatGPTAPI(String systemPrompt, String userPrompt) {
        // OpenAI API 키가 테스트용이면 더미 응답 반환
        if ("test-key".equals(openaiApiKey) || openaiApiKey == null || openaiApiKey.isEmpty()) {
            return generateDummyResponse(userPrompt);
        }

        try {
            RestTemplate restTemplate = new RestTemplate();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(openaiApiKey);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", "gpt-3.5-turbo");

            List<Map<String, String>> messages = new ArrayList<>();
            messages.add(Map.of("role", "system", "content", systemPrompt));
            messages.add(Map.of("role", "user", "content", userPrompt));

            requestBody.put("messages", messages);
            requestBody.put("max_tokens", 1000);
            requestBody.put("temperature", 0.7);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(
                    "https://api.openai.com/v1/chat/completions",
                    entity,
                    Map.class
            );

            Map<String, Object> responseBody = response.getBody();
            if (responseBody != null) {
                List<Map<String, Object>> choices = (List<Map<String, Object>>) responseBody.get("choices");
                if (choices != null && !choices.isEmpty()) {
                    Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                    if (message != null) {
                        return (String) message.get("content");
                    }
                }
            }

            return "OpenAI API에서 응답을 받지 못했습니다.";

        } catch (Exception e) {
            e.printStackTrace();
            return "AI 추천 서비스에 일시적인 문제가 발생했습니다. 잠시 후 다시 시도해주세요.\n오류: " + e.getMessage();
        }
    }

    private String generateDummyResponse(String userPrompt) {
        return """
            안녕하세요! 요청해주신 내용을 바탕으로 추천드리겠습니다.
            
            🎬 영화 추천:
            - 액션 영화를 좋아하신다면 최신 블록버스터 영화들을 추천드립니다
            - 드라마를 선호하신다면 감동적인 스토리의 영화들이 좋을 것 같습니다
            
            📚 웹툰 추천:
            - 장르에 맞는 인기 웹툰들을 추천드립니다
            - 완결된 작품과 연재 중인 작품을 골고루 추천합니다
            
            📖 웹소설 추천:
            - 선호하시는 장르의 인기 웹소설들을 추천드립니다
            
            🎮 게임 추천:
            - 플랫폼과 장르에 맞는 게임들을 추천드립니다
            
            현재 테스트 모드로 운영 중입니다. 실제 OpenAI API 키가 설정되면 더 정확한 개인화된 추천을 받으실 수 있습니다.
            
            요청사항: """ + userPrompt;
    }

    private Map<String, Object> parseAndMatchContent(String llmResponse) {
        Map<String, Object> result = new HashMap<>();

        // LLM 응답에서 실제 콘텐츠 제목을 추출하고 데이터베이스와 매칭
        // 현재는 기본값으로 빈 리스트 반환
        result.put("recommendedMovies", new ArrayList<>());
        result.put("recommendedNovels", new ArrayList<>());
        result.put("recommendedWebtoons", new ArrayList<>());
        result.put("recommendedOTT", new ArrayList<>());
        result.put("recommendedGames", new ArrayList<>());

        return result;
    }

    public List<LLMRecommendationRequest> getUserRecommendationHistory(String username) {
        return llmRequestRepository.findTop10ByUsernameOrderByCreatedAtDesc(username);
    }
}