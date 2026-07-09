package com.example.crawler.ingest;

import com.example.crawler.ingest.rule.PlatformRule;
import com.example.crawler.ingest.rule.RuleRegistry;
import com.example.shared.entity.Content;
import com.example.shared.entity.Domain;
import com.example.shared.entity.PlatformData;
import com.example.shared.entity.RawItem;
import com.example.shared.repository.ContentRepository;
import com.example.shared.repository.PlatformDataRepository;
import com.example.shared.repository.RawItemRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.PropertyAccessorFactory;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * ingest 오케스트레이터 (구 BatchTransformService/Optimized + UpsertService + ContentUpsertService
 *  + DomainCoreUpsertService + ContentMergeService 대체).
 * 흐름: ① claim(잠금→processed 마킹→커밋으로 락 해제) → ② item별 트랜잭션 격리로
 *      변환→중복병합|신규저장 → ③ TransformRun 감사 기록.
 * 실패 시맨틱(스펙 §5): item 실패는 FAILED 기록 후 배치 계속 / 미지 플랫폼 FAILED / 제목 blank SKIPPED.
 */
@Slf4j
public class IngestPipeline {

    private final RawItemRepository rawRepo;
    private final TransformRunRepository runRepo;
    private final RuleRegistry ruleRegistry;
    private final DraftAssembler assembler;
    private final DomainCatalog catalog;
    private final ContentRepository contentRepo;
    private final PlatformDataRepository platformRepo;
    private final TransactionTemplate tx;

    public IngestPipeline(RawItemRepository rawRepo, TransformRunRepository runRepo,
                          RuleRegistry ruleRegistry, DraftAssembler assembler, DomainCatalog catalog,
                          ContentRepository contentRepo, PlatformDataRepository platformRepo,
                          TransactionTemplate tx) {
        this.rawRepo = rawRepo;
        this.runRepo = runRepo;
        this.ruleRegistry = ruleRegistry;
        this.assembler = assembler;
        this.catalog = catalog;
        this.contentRepo = contentRepo;
        this.platformRepo = platformRepo;
        this.tx = tx;
    }

    /** @return claim된 건수 (스케줄러가 0이 될 때까지 반복 호출) */
    public int processBatch(int batchSize) {
        List<RawItem> batch = tx.execute(s -> {
            List<RawItem> items = rawRepo.lockNextBatch(batchSize);
            for (RawItem raw : items) {                    // claim: 실패해도 재선택 안 됨 (독약 루프 방지)
                raw.setProcessed(true);
                raw.setProcessedAt(Instant.now());
            }
            rawRepo.saveAll(items);
            return items;
        });

        Set<Long> seenContentIds = new HashSet<>();        // 배치 내 중복 → SUCCESS_DUPLICATE (기존 유지)
        for (RawItem raw : batch) {
            TransformRun run = newRun(raw);
            try {
                tx.execute(s -> {
                    processOne(raw, run, seenContentIds);
                    return null;
                });
            } catch (Exception e) {                        // 개선 ①: item 격리 — 배치 계속
                run.setProducedContentId(null);            // 롤백된 contentId가 감사에 남으면 안 됨
                run.setStatus("FAILED");
                run.setError(e.toString());
                log.warn("ingest 실패 rawId={} platform={}: {}", raw.getRawId(), raw.getPlatformName(), e.toString());
            } finally {
                run.setFinishedAt(Instant.now());
                try {
                    runRepo.save(run);
                } catch (Exception ex) {                   // 감사 기록 실패가 배치를 죽이면 안 됨 (T6 리뷰)
                    log.error("TransformRun 기록 실패 rawId={}", raw.getRawId(), ex);
                }
            }
        }
        return batch.size();
    }

    private void processOne(RawItem raw, TransformRun run, Set<Long> seenContentIds) {
        PlatformRule rule = ruleRegistry.resolve(raw.getDomain(), raw.getPlatformName()); // 미지 플랫폼 → IAE → FAILED (②)
        run.setRulePath(ruleRegistry.pathOf(raw.getPlatformName()));
        DraftAssembler.IngestDraft draft = assembler.assemble(raw.getSourcePayload(), rule);

        String title = draft.content().getMasterTitle();
        if (title == null || title.isBlank()) {            // 개선 ③: 명시적 SKIPPED
            run.setStatus("SKIPPED");
            run.setError("master_title 없음");
            return;
        }

        fillPlatformIds(raw, draft.platformData());
        Domain domain = Domain.valueOf(rule.domain());

        Content merged = findAndMergeDuplicate(domain, draft);
        if (merged == null) merged = findByPlatformIdentity(domain, draft); // 재수집 = 같은 작품 갱신
        Long contentId;
        if (merged != null) {
            contentId = merged.getContentId();
        } else {
            Content saved = contentRepo.save(draft.content());
            draft.platformData().setContent(saved);
            platformRepo.save(draft.platformData());
            catalog.save(domain, draft.domainEntity());
            contentId = saved.getContentId();
        }
        run.setProducedContentId(contentId);
        run.setStatus(seenContentIds.contains(contentId) ? "SUCCESS_DUPLICATE" : "SUCCESS");
        seenContentIds.add(contentId);
    }

    /** psid/url 결정: raw 컬럼 우선 → yml 매핑(draft) → payload 공용 키 (구 fallback 체인의 축소 보존). */
    private void fillPlatformIds(RawItem raw, PlatformData pd) {
        if (notBlank(raw.getPlatformSpecificId())) pd.setPlatformSpecificId(raw.getPlatformSpecificId());
        else if (!notBlank(pd.getPlatformSpecificId()))    // blank도 fallback (구 firstNonNull 패리티, T6 리뷰)
            pd.setPlatformSpecificId(Values.str(Values.deepGet(raw.getSourcePayload(), "platformSpecificId")));
        if (notBlank(raw.getUrl())) pd.setUrl(raw.getUrl());
        else if (!notBlank(pd.getUrl()))
            pd.setUrl(Values.str(Values.deepGet(raw.getSourcePayload(), "url")));
    }

    /** 후보 중 제목 동일 작품이 있으면 병합 (빈 후보/불일치 = null → 신규 경로). */
    private Content findAndMergeDuplicate(Domain domain, DraftAssembler.IngestDraft draft) {
        for (Content candidate : catalog.duplicateCandidates(domain, draft.domainEntity())) {
            if (Values.sameTitle(draft.content().getMasterTitle(), candidate.getMasterTitle())) {
                mergeInto(domain, candidate, draft);
                return candidate;
            }
        }
        return null;
    }

    /**
     * 재수집 라우팅: 중복후보가 없어도 (platform, psid)가 이미 아는 행이면 그 작품으로 병합.
     * 없으면 null → 신규 경로. (신규경로가 같은 psid를 다시 insert → uk_platform_id 위반으로
     * 재수집이 영구 FAILED 루프에 빠지던 Critical 수정)
     */
    private Content findByPlatformIdentity(Domain domain, DraftAssembler.IngestDraft draft) {
        PlatformData pd = draft.platformData();
        if (!notBlank(pd.getPlatformSpecificId())) return null;
        Content owner = platformRepo.findByPlatformNameAndPlatformSpecificId(
                        pd.getPlatformName(), pd.getPlatformSpecificId())
                .map(PlatformData::getContent).orElse(null);
        if (owner == null) return null;
        mergeInto(domain, owner, draft);
        return owner;
    }

    /**
     * 기존 작품에 병합 (구 ContentMergeService.mergeContent 동작 보존):
     * ① Content는 null인 필드만 채움 ② PlatformData는 (platform,psid) 기존 행이 있으면
     *    url/attributes/lastSeenAt 갱신(재수집 반영 — 구 신규경로 upsert의 유용한 절반을 복원한
     *    의도적 결정), 없으면 추가
     * ③ 도메인 필드는 이번에 바인딩된 프로퍼티를 '덮어쓰기'.
     * ⚠ ③은 platforms 배열도 교체한다(크로스플랫폼 병합 시 기존 플랫폼 유실) — 기존 시스템과 동일한
     *   알려진 이슈로, 동작 보존 원칙에 따라 그대로 두고 별도 이슈로 다룬다.
     */
    private void mergeInto(Domain domain, Content existing, DraftAssembler.IngestDraft draft) {
        Content incoming = draft.content();
        if (existing.getOriginalTitle() == null) existing.setOriginalTitle(incoming.getOriginalTitle());
        if (existing.getReleaseDate() == null) existing.setReleaseDate(incoming.getReleaseDate());
        if (existing.getPosterImageUrl() == null) existing.setPosterImageUrl(incoming.getPosterImageUrl());
        if (existing.getSynopsis() == null) existing.setSynopsis(incoming.getSynopsis());
        contentRepo.save(existing);

        PlatformData pd = draft.platformData();
        platformRepo.findByPlatformNameAndPlatformSpecificId(pd.getPlatformName(), pd.getPlatformSpecificId())
                .ifPresentOrElse(prior -> {                // 재수집: 신선도 갱신 — 최신 크롤이 진실
                    prior.setAttributes(pd.getAttributes());
                    prior.setLastSeenAt(Instant.now());
                    if (notBlank(pd.getUrl())) prior.setUrl(pd.getUrl());
                    if (prior.getContent() != null && !Objects.equals(
                            prior.getContent().getContentId(), existing.getContentId()))
                        log.warn("크로스링크 PlatformData — (platform={}, psid={})가 contentId={}에 연결된 채 contentId={}로 병합됨",
                                pd.getPlatformName(), pd.getPlatformSpecificId(),
                                prior.getContent().getContentId(), existing.getContentId());
                    platformRepo.save(prior);              // content 포인터는 건드리지 않음
                }, () -> {
                    pd.setContent(existing);
                    platformRepo.save(pd);
                });

        catalog.findByContentId(domain, existing.getContentId()).ifPresentOrElse(existingEntity -> {
            BeanWrapper from = PropertyAccessorFactory.forBeanPropertyAccess(draft.domainEntity());
            BeanWrapper to = PropertyAccessorFactory.forBeanPropertyAccess(existingEntity);
            for (String prop : draft.boundDomainProps())
                to.setPropertyValue(prop, from.getPropertyValue(prop));
            catalog.save(domain, existingEntity);
        }, () -> log.warn("도메인 행 없음 — 도메인 필드 병합 스킵 contentId={}", existing.getContentId()));
    }

    private TransformRun newRun(RawItem raw) {
        TransformRun run = new TransformRun();
        run.setRawId(raw.getRawId());
        run.setPlatformName(raw.getPlatformName());
        run.setDomain(raw.getDomain());
        return run;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
