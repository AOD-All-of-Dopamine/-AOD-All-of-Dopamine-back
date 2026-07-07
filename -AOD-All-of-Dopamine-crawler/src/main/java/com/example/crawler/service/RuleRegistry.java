package com.example.crawler.service;

import com.example.crawler.rules.MappingRule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * classpath:rules/**\/*.yml 을 기동 시 전부 스캔해 platformName으로 인덱싱한다.
 * "새 플랫폼 추가 = yml 파일 1개 추가" — 자바 코드(switch) 수정 불필요.
 * yml 안의 platformName/domain 이 유일한 진실 공급원이다.
 */
@Slf4j
@Component
public class RuleRegistry {

    private final Map<String, MappingRule> byPlatform = new LinkedHashMap<>();
    private final Map<String, String> paths = new LinkedHashMap<>();

    public RuleRegistry(RuleLoader loader) {
        try {
            var resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources("classpath*:rules/**/*.yml");
            for (Resource res : resources) {
                String path = resourcePath(res);
                MappingRule rule = loader.load(path);
                if (rule.getPlatformName() == null || rule.getDomain() == null) {
                    throw new IllegalStateException(
                            "rule yml에 platformName/domain 누락: " + path);
                }
                MappingRule prev = byPlatform.put(rule.getPlatformName(), rule);
                if (prev != null) {
                    throw new IllegalStateException(
                            "platformName 중복: " + rule.getPlatformName() + " (" + path + ")");
                }
                paths.put(rule.getPlatformName(), path);
                log.info("룰 등록: {} → domain={} ({})", rule.getPlatformName(), rule.getDomain(), path);
            }
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("rules/**/*.yml 스캔 실패", e);
        }
        if (byPlatform.isEmpty()) {
            throw new IllegalStateException("발견된 rule yml이 없음 (classpath:rules/)");
        }
    }

    /**
     * Resource → RuleLoader가 이해하는 classpath 상대경로("rules/...") 추출.
     * 첫 "rules/" 매칭은 상위 디렉토리명(예: /opt/app-rules/)에 오탐할 수 있어,
     * "이후에 '!'가 없고 .yml로 끝나는" 마지막 "rules/" 후보를 취한다 (리뷰 F#2).
     */
    private String resourcePath(Resource res) throws java.io.IOException {
        String url = res.getURL().toString().replace('\\', '/');
        String found = null;
        int idx = url.indexOf("rules/");
        while (idx >= 0) {
            String candidate = url.substring(idx);
            if (candidate.endsWith(".yml") && !candidate.contains("!")) {
                found = candidate; // 더 뒤의(짧은) 후보로 계속 갱신
            }
            idx = url.indexOf("rules/", idx + 1);
        }
        if (found == null) throw new IllegalStateException("classpath rules 경로 아님: " + url);
        return found;
    }

    public Optional<MappingRule> byPlatform(String platformName) {
        return Optional.ofNullable(byPlatform.get(platformName));
    }

    /** (domain, platform) 쌍 매칭 — 기존 switch의 도메인 일치 의미를 보존한다. */
    public MappingRule resolve(String domain, String platformName) {
        MappingRule rule = byPlatform.get(platformName);
        if (rule == null) {
            throw new IllegalArgumentException("No rule for platform: " + platformName);
        }
        if (domain != null && !rule.getDomain().equalsIgnoreCase(domain)) {
            throw new IllegalArgumentException("Rule domain mismatch: platform=" + platformName
                    + " rule.domain=" + rule.getDomain() + " requested=" + domain);
        }
        return rule;
    }

    /** 룰의 원본 yml 경로 (TransformRun.rulePath 추적용). 미지 플랫폼이면 null. */
    public String pathOf(String platformName) {
        return paths.get(platformName);
    }

    public Collection<MappingRule> all() {
        return byPlatform.values();
    }

    public int size() {
        return byPlatform.size();
    }
}
