package com.example.crawler.ingest.rule;

import com.example.crawler.ingest.DomainCatalog;
import com.example.crawler.ingest.Values;
import com.example.shared.entity.Content;
import com.example.shared.entity.Domain;
import com.example.shared.entity.PlatformData;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.PropertyAccessorFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 기동 시 룰 yml 전부 스캔 → 파싱 → 검증 → platformName으로 인덱싱.
 * (구 service.RuleRegistry의 스캔/인덱싱 + RuleLoader 흡수 + 목적지 프로퍼티 기동검증 추가)
 * 검증 실패 = 부팅 실패: yml 오타·엔티티 rename 미반영이 배포 전에 잡힌다 (조용한 유실 차단).
 * 파일 단위로 에러를 감싸므로 어떤 yml이 문제인지 메시지만으로 특정 가능하다.
 */
public class RuleRegistry {

    private final Map<String, PlatformRule> byPlatform = new LinkedHashMap<>();
    private final Map<String, String> paths = new LinkedHashMap<>();

    public RuleRegistry(String locationPattern, DomainCatalog catalog) {
        Resource[] resources;
        try {
            resources = new PathMatchingResourcePatternResolver().getResources(locationPattern);
        } catch (Exception e) {
            throw new IllegalStateException("rule yml 스캔 실패: " + locationPattern, e);
        }
        for (Resource res : resources) {
            String url;
            try {
                url = res.getURL().toString();
            } catch (Exception e) {
                throw new IllegalStateException("rule 리소스 URL 확인 실패: " + res, e);
            }
            try {
                Map<String, Object> yaml;
                try (InputStream in = res.getInputStream()) {
                    yaml = new Yaml().load(in);
                }
                PlatformRule rule = PlatformRule.parse(url, yaml);
                validate(url, rule, catalog);
                if (byPlatform.put(rule.platformName(), rule) != null)
                    throw new IllegalStateException("platformName 중복: " + rule.platformName());
                paths.put(rule.platformName(), shortPath(url));
            } catch (IllegalStateException e) {
                throw e.getMessage() != null && e.getMessage().contains(url)
                        ? e
                        : new IllegalStateException(url + ": " + e.getMessage(), e);
            } catch (RuntimeException | java.io.IOException e) {
                // 파싱/검증 중 어떤 예외든 실패한 파일 경로를 메시지에 보존한다 (T2 리뷰 후속)
                throw new IllegalStateException(url + ": rule 로드 실패 — " + e, e);
            }
        }
        if (byPlatform.isEmpty())
            throw new IllegalStateException("발견된 rule yml이 없음: " + locationPattern);
    }

    /** 기동 검증: 모든 목적지 프로퍼티 실존 + normalizer 어휘/타입 확인. */
    private void validate(String path, PlatformRule rule, DomainCatalog catalog) {
        Domain domain = Domain.valueOf(rule.domain());
        BeanWrapper master = PropertyAccessorFactory.forBeanPropertyAccess(new Content());
        BeanWrapper dom = PropertyAccessorFactory.forBeanPropertyAccess(catalog.create(domain, new Content()));
        BeanWrapper platform = PropertyAccessorFactory.forBeanPropertyAccess(new PlatformData());

        for (String dst : rule.mappings().values()) requireProperty(path, dst, master, dom, platform);
        for (String dst : rule.defaults().keySet()) requireProperty(path, dst, master, dom, platform);

        for (Map.Entry<String, List<String>> n : rule.normalizers().entrySet()) {
            requireProperty(path, n.getKey(), master, dom, platform);
            String prop = n.getKey().substring("master.".length());
            if (!String.class.equals(master.getPropertyType(prop)))
                throw new IllegalStateException(path + ": normalizer 대상은 String 프로퍼티여야 함 — " + n.getKey()
                        + " (타입: " + master.getPropertyType(prop) + ")");   // T4 리뷰 후속
            for (String step : n.getValue())
                if (!Values.NORMALIZERS.contains(step))
                    throw new IllegalStateException(path + ": 알 수 없는 normalizer '" + step + "'");
        }
    }

    private void requireProperty(String path, String dst, BeanWrapper master, BeanWrapper dom, BeanWrapper platform) {
        if (dst.startsWith("attr.")) return;                         // JSONB — 자유 키
        BeanWrapper acc = dst.startsWith("master.") ? master : dst.startsWith("platform.") ? platform : dom;
        String prop = dst.substring(dst.indexOf('.') + 1);
        if (!acc.isWritableProperty(prop))
            throw new IllegalStateException(path + ": 존재하지 않는 프로퍼티 '" + dst + "'");
    }

    /** (domain, platform) 매칭 — 구 레지스트리와 동일 시맨틱. */
    public PlatformRule resolve(String domain, String platformName) {
        PlatformRule rule = byPlatform.get(platformName);
        if (rule == null) throw new IllegalArgumentException("No rule for platform: " + platformName);
        if (domain != null && !rule.domain().equalsIgnoreCase(domain))
            throw new IllegalArgumentException("Rule domain mismatch: platform=" + platformName
                    + " rule.domain=" + rule.domain() + " requested=" + domain);
        return rule;
    }

    /** TransformRun.rulePath 기록용. */
    public String pathOf(String platformName) {
        return paths.get(platformName);
    }

    private static String shortPath(String url) {
        int i = url.lastIndexOf("/rules/");
        return i >= 0 ? url.substring(i + 1) : url;
    }
}
