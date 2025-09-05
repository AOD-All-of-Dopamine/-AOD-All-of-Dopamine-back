package com.example.AOD.v2.service;


import com.example.AOD.v2.rules.MappingRule;
import com.example.AOD.v2.rules.NormalizerStep;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class TransformEngine {

    public static class MasterDoc extends HashMap<String, Object> { }
    public static class PlatformDoc extends HashMap<String, Object> {
        public PlatformDoc() {
            put("attributes", new HashMap<String, Object>());
        }
        @SuppressWarnings("unchecked")
        public Map<String,Object> attributes() { return (Map<String,Object>) get("attributes"); }
    }

    public static Object deepGet(Map<String, Object> obj, String path) {
        String[] parts = path.split("\\.");
        Object cur = obj;
        for (String p : parts) {
            if (!(cur instanceof Map<?, ?> m) || !m.containsKey(p)) return null;
            cur = m.get(p);
        }
        return cur;
    }

    public void applyNormalizers(MasterDoc doc, List<NormalizerStep> steps) {
        if (steps == null) return;
        for (NormalizerStep step : steps) {
            String type = step.getType();
            if (step.getFields() == null) continue;
            for (String f : step.getFields()) {
                Object v = doc.get(f);
                if (!(v instanceof String s)) continue;
                switch (type) {
                    case "lowercase" -> doc.put(f, s.toLowerCase());
                    case "strip_parentheses" -> doc.put(f, s.replaceAll("\\([^)]*\\)", ""));
                    case "collapse_spaces" -> doc.put(f, s.replaceAll("\\s+", " ").trim());
                    default -> { /* ignore */ }
                }
            }
        }
    }

    /** raw JSON(Map) → (master, platform) */
    public Map.Entry<MasterDoc, PlatformDoc> transform(Map<String, Object> raw, MappingRule rule) {
        MasterDoc master = new MasterDoc();
        PlatformDoc platform = new PlatformDoc();
        platform.put("platformName", rule.getPlatformName());

        Map<String, String> fm = rule.getFieldMappings();
        if (fm != null) {
            for (var e : fm.entrySet()) {
                String src = e.getKey();
                String dst = e.getValue();
                Object val = deepGet(raw, src);
                if (val == null) continue;

                if (dst.startsWith("platform.")) {
                    String rest = dst.substring("platform.".length());
                    if (rest.startsWith("attributes.")) {
                        String key = rest.substring("attributes.".length());
                        platform.attributes().put(key, val);
                    } else {
                        platform.put(rest, val);
                    }
                } else {
                    master.put(dst, val);
                }
            }
        }
        applyNormalizers(master, rule.getNormalizers());
        return Map.entry(master, platform);
    }
}
