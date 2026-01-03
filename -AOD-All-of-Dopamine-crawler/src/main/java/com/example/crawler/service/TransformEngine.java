package com.example.crawler.service;


import com.example.crawler.rules.MappingRule;
import com.example.crawler.rules.NormalizerStep;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class TransformEngine {

    public static class MasterDoc extends HashMap<String, Object> { }
    public static class PlatformDoc extends HashMap<String, Object> {
        public PlatformDoc(){ put("attributes", new HashMap<String,Object>()); }
        @SuppressWarnings("unchecked")
        public Map<String,Object> attributes(){ return (Map<String,Object>) get("attributes"); }
    }
    public static class DomainDoc extends HashMap<String, Object> { }

    public static Object deepGet(Object obj, String path) {
        if (obj == null) return null;
        String[] parts = path.split("\\.");
        Object cur = obj;
        for (String rawPart : parts) {
            // developers[0] 또는 단순 "developers" / "0" 둘 다 지원
            String part = rawPart;
            Integer bracketIdx = null;

            // bracket 표기 처리
            if (part.contains("[") && part.endsWith("]")) {
                int i = part.indexOf('[');
                String head = part.substring(0, i);
                String idxStr = part.substring(i+1, part.length()-1);
                part = head;
                try { bracketIdx = Integer.parseInt(idxStr); } catch (Exception ignored) {}
            }

            if (cur instanceof Map<?,?> m) {
                cur = m.get(part);
            } else {
                return null;
            }

            if (bracketIdx != null) {
                if (cur instanceof List<?> list) {
                    int i = bracketIdx;
                    if (i < 0 || i >= list.size()) return null;
                    cur = list.get(i);
                } else {
                    return null;
                }
            }
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
                    case "nfkc" -> doc.put(f, java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFKC));
                    case "strip_brackets" -> doc.put(f, s.replaceAll("\\[[^\\]]*\\]", ""));
                    case "strip_series_qualifiers" -> doc.put(f, s.replaceAll("(시즌\\s*\\d+|외전|스페셜)$","").trim());
                    default -> { /* noop */ }
                }
            }
        }
    }

    /** raw(Map) -> (master, platform, domain) */
    public Triple transform(Map<String,Object> raw, MappingRule rule) {
        MasterDoc master = new MasterDoc();
        PlatformDoc platform = new PlatformDoc();
        platform.put("platformName", rule.getPlatformName());
        DomainDoc domain = new DomainDoc();

        Map<String,String> fm = rule.getFieldMappings();
        if (fm != null) {
            for (var e : fm.entrySet()) {
                String src = e.getKey();
                String dst = e.getValue();

                Object val = deepGet(raw, src);

                // 값이 null일 경우, platform.attributes 필드에 한해 기본값 설정
                if (val == null && dst.startsWith("platform.attributes.")) {
                    String attrName = dst.substring("platform.attributes.".length());
                    if (attrName.contains("count") || attrName.contains("runtime")) {
                        val = 0;
                    } else if (attrName.equals("cast") || attrName.equals("crew")) {
                        val = Collections.emptyList();
                    } else {
                        val = "";
                    }
                }

                if (val == null) continue;

                if (dst.startsWith("platform.")) {
                    String rest = dst.substring("platform.".length());
                    if (rest.startsWith("attributes.")) {
                        platform.attributes().put(rest.substring("attributes.".length()), val);
                    } else {
                        platform.put(rest, val);
                    }
                } else if (dst.startsWith("domain.")) {
                    domain.put(dst.substring("domain.".length()), val);
                } else {
                    master.put(dst, val);
                }
            }
        }
        applyNormalizers(master, rule.getNormalizers());
        return new Triple(master, platform, domain);
    }

    public record Triple(MasterDoc master, PlatformDoc platform, DomainDoc domain) {}
}


