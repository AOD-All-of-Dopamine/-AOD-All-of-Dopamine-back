package com.example.AOD.api.repository;

import com.example.shared.entity.Content;
import com.example.shared.repository.ContentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.Query;

import java.lang.reflect.Method;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 목록 경로 성인 제외 가드 (2026-08 Steam 정제 ①).
 *
 * 성인 제외는 DB 쿼리 레벨에서 걸리므로 Mockito 단위 테스트로는 검증 불가 —
 * 대신 ContentRepository의 모든 Page 반환(=목록 경로) 쿼리가 is_adult 필터를
 * 선언했는지를 어노테이션 리플렉션으로 지킨다. 새 목록 쿼리를 필터 없이 추가하면 여기서 깨진다.
 *
 * 상세 경로(findById)와 추천용 findByContentIdIn은 목록이 아니므로 대상 밖 (상세 접근 허용이 요구 범위).
 */
class ContentRepositoryAdultFilterTest {

    /** JPQL은 c.isAdult = false, native SQL은 is_adult = false */
    private static boolean hasAdultFilter(String query) {
        return query.contains("isAdult = false") || query.contains("is_adult = false");
    }

    private static List<Method> pageReturningQueryMethods() {
        return java.util.Arrays.stream(ContentRepository.class.getDeclaredMethods())
                .filter(m -> Page.class.isAssignableFrom(m.getReturnType()))
                .collect(Collectors.toList());
    }

    @Test
    void everyListQueryDeclaresAdultExclusion() {
        List<Method> listMethods = pageReturningQueryMethods();
        assertFalse(listMethods.isEmpty(), "목록 경로 메서드가 하나도 없을 리 없음 — 리플렉션 대상 확인");

        for (Method m : listMethods) {
            Query q = m.getAnnotation(Query.class);
            assertTrue(q != null,
                    m.getName() + ": 목록 경로는 @Query로 is_adult 필터를 선언해야 함 (파생 쿼리 금지)");
            assertTrue(hasAdultFilter(q.value()),
                    m.getName() + ": 본 쿼리에 성인 제외(is_adult/isAdult = false)가 없음");
            if (!q.countQuery().isEmpty()) {
                assertTrue(hasAdultFilter(q.countQuery()),
                        m.getName() + ": countQuery에 성인 제외가 없음 — 페이지 수가 어긋난다");
            }
        }
    }

    @Test
    void findWorksHasGameReviewCountAxis() {
        // 게임 축(reviewCountMin) — 웹툰 3축과 같은 EXISTS 패턴으로 WORKS_FILTER에 존재해야 함
        assertTrue(ContentRepository.WORKS_FILTER.contains(":reviewCountMin"),
                "WORKS_FILTER에 reviewCountMin 파라미터가 없음");
        assertTrue(ContentRepository.WORKS_FILTER.contains("EXISTS (SELECT 1 FROM game_contents"),
                "WORKS_FILTER에 game_contents EXISTS 서브쿼리가 없음");
        assertTrue(ContentRepository.WORKS_FILTER.contains("is_adult = false"),
                "WORKS_FILTER에 성인 제외가 없음");
    }

    @Test
    void rankingMappedContentQueriesExcludeAdult() throws NoSuchMethodException {
        // 랭킹 매핑 조회 2종 — 매핑된 콘텐츠가 성인이면 행 제외, 미매핑(c IS NULL) 행은 유지
        // (미매핑 행의 크롤 시점 title/썸네일 노출은 판별 불가로 허용 — 플랜 편차 기록)
        Class<?> repo = com.example.shared.repository.ExternalRankingRepository.class;
        for (Method m : new Method[]{
                repo.getMethod("findByPlatformWithContent", String.class),
                repo.getMethod("findAllWithContent")}) {
            Query q = m.getAnnotation(Query.class);
            assertTrue(q != null && hasAdultFilter(q.value()),
                    m.getName() + ": 매핑 콘텐츠 성인 제외가 없음");
            assertTrue(q.value().contains("c IS NULL OR"),
                    m.getName() + ": 미매핑 행 유지(c IS NULL OR ...) 조건이 없음");
        }
    }

    @Test
    void detailPathIsNotFiltered() {
        // 상세 접근 허용 계약: findById는 JpaRepository 상속 그대로 — 성인 필터가 덧씌워지지 않았어야 함
        boolean overridden = java.util.Arrays.stream(ContentRepository.class.getDeclaredMethods())
                .anyMatch(m -> m.getName().equals("findById"));
        assertFalse(overridden, "findById를 재정의하면 성인 상세 접근 허용 계약이 깨질 수 있음");
        // Content 엔티티에 isAdult가 실제 존재하는지 (컬럼명 오타 방지 겸 컴파일 타임 보증)
        Content c = new Content();
        c.setIsAdult(true);
        assertTrue(c.getIsAdult());
    }
}
