package com.example.crawler.crawl.support;

import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * Deterministic JSON for golden-master comparison: keys sorted, newlines forced to '\n'.
 * Mirrors the intent of CollectorService.sha256Canonical but optimized for human-readable diffs.
 */
public final class CanonicalJson {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

    private CanonicalJson() {
    }

    public static String serialize(Object value) {
        try {
            DefaultIndenter indenter = new DefaultIndenter("  ", "\n");
            DefaultPrettyPrinter printer = new DefaultPrettyPrinter();
            printer.indentObjectsWith(indenter);
            printer.indentArraysWith(indenter);
            return MAPPER.writer(printer).writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("CanonicalJson serialize failed", e);
        }
    }
}
