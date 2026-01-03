package com.example.crawler.service.similarity;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 작품 제목 유사도 검사 서비스 (간소화된 구현)
 * - 편집거리/임계값 사용하지 않음
 * - 제목 정규화 후 정확히 일치하는 경우에만 동일하다고 판단
 */
@Slf4j
@Service
public class ContentSimilarityService {

    /**
     * 두 제목의 유사도를 계산합니다.
     * 정규화한 제목이 동일하면 1.0, 아니면 0.0을 반환합니다.
     */
    public double calculateSimilarity(String title1, String title2) {
        if (title1 == null || title2 == null) return 0.0;
        String n1 = normalizeTitle(title1);
        String n2 = normalizeTitle(title2);
        return n1.equals(n2) ? 1.0 : 0.0;
    }

    /**
     * 정규화한 제목이 정확히 같으면 true
     */
    public boolean isSameTitle(String title1, String title2) {
        return calculateSimilarity(title1, title2) == 1.0;
    }

    private String normalizeTitle(String title) {
        if (title == null) return "";
        return title
                .toLowerCase()
                .replaceAll("[\\s\\-_:;,.'\"!?()\\[\\]{}]", "")
                .trim();
    }
}


