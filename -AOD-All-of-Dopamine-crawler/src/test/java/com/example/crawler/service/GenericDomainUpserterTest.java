package com.example.crawler.service;

import com.example.crawler.rules.DomainObjectMapping;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GenericDomainUpserterTest {

    /** 테스트용 단순 빈 (PropertyAccessor 대상) */
    public static class TestEntity {
        private String status;
        private Integer count;
        private List<String> genres;
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public Integer getCount() { return count; }
        public void setCount(Integer count) { this.count = count; }
        public List<String> getGenres() { return genres; }
        public void setGenres(List<String> genres) { this.genres = genres; }
    }

    private final GenericDomainUpserter upserter = new GenericDomainUpserter();

    private DomainObjectMapping mapping(String targetField, String type) {
        DomainObjectMapping m = new DomainObjectMapping();
        m.setTargetField(targetField);
        m.setType(type);
        return m;
    }

    @Test
    void setsFieldsViaReflectionWithTypeConversion() {
        TestEntity e = new TestEntity();
        upserter.upsert(e,
                Map.of("status", "연재중", "count", "42", "genres", List.of("판타지")),
                Map.of("status", mapping("status", "string"),
                       "count", mapping("count", "integer"),
                       "genres", mapping("genres", "list")));
        assertEquals("연재중", e.getStatus());
        assertEquals(42, e.getCount());
        assertEquals(List.of("판타지"), e.getGenres());
    }

    @Test
    void valueMapTranslatesRawValuesDeclaratively() {
        // RF-5: 도메인 값 변환은 범용 변환기 하드코딩(webtoon_status)이 아니라 yml valueMap 선언으로
        DomainObjectMapping m = mapping("status", "string");
        m.setValueMap(Map.of("true", "완결", "false", "연재중"));

        TestEntity e = new TestEntity();
        upserter.upsert(e, Map.of("status", "true"), Map.of("status", m));
        assertEquals("완결", e.getStatus());

        TestEntity e2 = new TestEntity();
        upserter.upsert(e2, Map.of("status", "false"), Map.of("status", m));
        assertEquals("연재중", e2.getStatus());
    }

    @Test
    void valueMapPassesThroughUnmappedValues() {
        DomainObjectMapping m = mapping("status", "string");
        m.setValueMap(Map.of("true", "완결"));
        TestEntity e = new TestEntity();
        upserter.upsert(e, Map.of("status", "휴재"), Map.of("status", m));
        assertEquals("휴재", e.getStatus()); // 맵에 없으면 원본 유지
    }

    @Test
    void valueMapDoesNotStringifyUnmappedNonStringValues() {
        // 리뷰 F#5: valueMap 미매칭 시 비문자열 값의 타입을 훼손하면 안 된다
        DomainObjectMapping m = mapping("genres", "list");
        m.setValueMap(Map.of("something", "다른것"));
        TestEntity e = new TestEntity();
        upserter.upsert(e, Map.of("genres", List.of("판타지", "액션")), Map.of("genres", m));
        assertEquals(List.of("판타지", "액션"), e.getGenres()); // "[판타지, 액션]" 문자열화 금지
    }
}
