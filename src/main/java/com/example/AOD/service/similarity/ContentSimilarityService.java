package com.example.AOD.service.similarity;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 작품 제목 유사도 검사 서비스
 * Levenshtein Distance 알고리즘 기반
 */
@Slf4j
@Service
public class ContentSimilarityService {

    private static final double DEFAULT_SIMILARITY_THRESHOLD = 0.85;

    /**
     * 두 제목의 유사도를 계산 (0.0 ~ 1.0)
     * @param title1 제목 1
     * @param title2 제목 2
     * @return 유사도 (1.0에 가까울수록 유사)
     */
    public double calculateSimilarity(String title1, String title2) {
        if (title1 == null || title2 == null) {
            return 0.0;
        }

        // 정규화: 공백 제거, 소문자 변환
        String normalized1 = normalizeTitle(title1);
        String normalized2 = normalizeTitle(title2);

        if (normalized1.equals(normalized2)) {
            return 1.0;
        }

        // Levenshtein Distance 계산
        int distance = levenshteinDistance(normalized1, normalized2);
        int maxLength = Math.max(normalized1.length(), normalized2.length());

        if (maxLength == 0) {
            return 1.0;
        }

        return 1.0 - ((double) distance / maxLength);
    }

    /**
     * 두 제목이 유사한지 판단 (기본 임계값 85%)
     */
    public boolean isSimilar(String title1, String title2) {
        return isSimilar(title1, title2, DEFAULT_SIMILARITY_THRESHOLD);
    }

    /**
     * 두 제목이 유사한지 판단 (커스텀 임계값)
     */
    public boolean isSimilar(String title1, String title2, double threshold) {
        double similarity = calculateSimilarity(title1, title2);
        log.debug("유사도 검사: '{}' vs '{}' = {}", title1, title2, similarity);
        return similarity >= threshold;
    }

    /**
     * 제목 정규화
     * - 공백, 특수문자 제거
     * - 소문자 변환
     * - 한글/영문/숫자만 남김
     */
    private String normalizeTitle(String title) {
        if (title == null) {
            return "";
        }

        return title
                .toLowerCase()
                .replaceAll("[\\s\\-_:;,.'\"!?()\\[\\]{}]", "")  // 공백 및 특수문자 제거
                .trim();
    }

    /**
     * Levenshtein Distance 알고리즘
     * 두 문자열 간의 편집 거리(삽입, 삭제, 치환 횟수) 계산
     */
    private int levenshteinDistance(String s1, String s2) {
        int len1 = s1.length();
        int len2 = s2.length();

        int[][] dp = new int[len1 + 1][len2 + 1];

        for (int i = 0; i <= len1; i++) {
            dp[i][0] = i;
        }

        for (int j = 0; j <= len2; j++) {
            dp[0][j] = j;
        }

        for (int i = 1; i <= len1; i++) {
            for (int j = 1; j <= len2; j++) {
                int cost = (s1.charAt(i - 1) == s2.charAt(j - 1)) ? 0 : 1;

                dp[i][j] = Math.min(
                        Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                        dp[i - 1][j - 1] + cost
                );
            }
        }

        return dp[len1][len2];
    }
}
