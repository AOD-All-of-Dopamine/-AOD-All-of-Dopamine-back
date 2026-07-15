package com.example.crawler.ingest;

import com.example.crawler.ingest.rule.PlatformRule;
import com.example.shared.entity.Content;
import com.example.shared.entity.Domain;
import com.example.shared.entity.PlatformData;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.PropertyAccessorFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * payload + rule → typed IngestDraft 조립 (구 TransformEngine.transform 대체).
 * 목적지 접두사: master.*=Content / domain.*=도메인 엔티티(리플렉션, 프로퍼티명 그대로)
 *              / platform.*=PlatformData / attr.*=attributes JSONB 키 리터럴.
 * 저장은 하지 않는다 — 그건 IngestPipeline의 일.
 */
public class DraftAssembler {

    private final DomainCatalog catalog;

    public DraftAssembler(DomainCatalog catalog) {
        this.catalog = catalog;
    }

    /** 조립 산물. boundDomainProps = 병합 시 덮어쓸 도메인 프로퍼티 목록. */
    public record IngestDraft(Content content, Object domainEntity, PlatformData platformData,
                              List<String> boundDomainProps) {}

    public IngestDraft assemble(Map<String, Object> payload, PlatformRule rule) {
        Domain domain = Domain.valueOf(rule.domain());
        Content content = new Content();
        content.setDomain(domain);
        Object domainEntity = catalog.create(domain, content);
        PlatformData pd = new PlatformData();
        pd.setPlatformName(rule.platformName());
        pd.setAttributes(new HashMap<>());
        pd.setLastSeenAt(Instant.now());

        BeanWrapper master = PropertyAccessorFactory.forBeanPropertyAccess(content);
        BeanWrapper dom = PropertyAccessorFactory.forBeanPropertyAccess(domainEntity);
        BeanWrapper platform = PropertyAccessorFactory.forBeanPropertyAccess(pd);
        List<String> boundDomainProps = new ArrayList<>();

        for (Map.Entry<String, String> e : rule.mappings().entrySet()) {
            Object value = Values.deepGet(payload, e.getKey());
            String dst = e.getValue();
            if (value == null) value = rule.defaults().get(dst);
            if (value == null) continue;                                  // 선언된 default 없으면 스킵 (RF-3)
            if (dst.startsWith("attr.")) {
                pd.getAttributes().put(dst.substring(5), value);           // JSONB 키 리터럴 그대로
            } else if (dst.startsWith("master.")) {
                bind(master, dst.substring(7), value);
            } else if (dst.startsWith("platform.")) {
                bind(platform, dst.substring(9), value);
            } else {                                                       // domain.
                String prop = dst.substring(7);
                bind(dom, prop, value);
                boundDomainProps.add(prop);
            }
        }

        // normalizers — master 필드에만 적용 (구 엔진과 동일)
        for (Map.Entry<String, List<String>> n : rule.normalizers().entrySet()) {
            String prop = n.getKey().substring(7);
            if (master.getPropertyValue(prop) instanceof String s)
                master.setPropertyValue(prop, Values.normalize(s, n.getValue()));
        }

        // master.platforms 주입: [자기 플랫폼명] + platformsFrom 병합 — yml 매핑과 무관하게 항상 채움 (RF-4)
        // (2026-07 contents로 승격 — 병합 시 기존∪신규 합집합은 IngestPipeline.mergeInto 담당)
        List<String> platforms = new ArrayList<>();
        platforms.add(rule.platformName());
        for (String attrKey : rule.platformsFrom())
            if (pd.getAttributes().get(attrKey) instanceof List<?> extra)
                for (Object v : extra) if (v instanceof String s) platforms.add(s);
        content.setPlatforms(platforms);

        return new IngestDraft(content, domainEntity, pd, boundDomainProps);
    }

    /** 목적지 프로퍼티 타입에 맞춰 변환 후 세팅. 프로퍼티 부재는 RuleRegistry 기동검증이 사전 차단. */
    private void bind(BeanWrapper accessor, String property, Object value) {
        accessor.setPropertyValue(property, Values.convert(value, accessor.getPropertyType(property)));
    }
}
