package com.example.crawler.crawl.support;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CanonicalJsonTest {

    @Test
    void sortsKeysAndUsesLfNewlines() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("b", 2);          // inserted out of order on purpose
        m.put("a", 1);

        String json = CanonicalJson.serialize(m);

        assertThat(json).isEqualTo("{\n  \"a\" : 1,\n  \"b\" : 2\n}");
    }

    @Test
    void isDeterministicRegardlessOfInsertionOrder() {
        Map<String, Object> m1 = new LinkedHashMap<>();
        m1.put("x", "1");
        m1.put("y", "2");
        Map<String, Object> m2 = new LinkedHashMap<>();
        m2.put("y", "2");
        m2.put("x", "1");

        assertThat(CanonicalJson.serialize(m1)).isEqualTo(CanonicalJson.serialize(m2));
    }
}
