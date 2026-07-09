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
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
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
                run.setStatus("FAILED");
                run.setError(e.toString());
                log.warn("ingest 실패 rawId={} platform={}: {}", raw.getRawId(), raw.getPlatformName(), e.toString());
            } finally {
                run.setFinishedAt(Instant.now());
                runRepo.save(run);
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
        else if (pd.getPlatformSpecificId() == null)
            pd.setPlatformSpecificId(Values.str(Values.deepGet(raw.getSourcePayload(), "platformSpecificId")));
        if (notBlank(raw.getUrl())) pd.setUrl(raw.getUrl());
        else if (pd.getUrl() == null)
            pd.setUrl(Values.str(Values.deepGet(raw.getSourcePayload(), "url")));
    }

    /** Task 7에서 병합 구현 — 지금은 후보 순회만 (빈 후보 = null). */
    private Content findAndMergeDuplicate(Domain domain, DraftAssembler.IngestDraft draft) {
        for (Content candidate : catalog.duplicateCandidates(domain, draft.domainEntity())) {
            if (Values.sameTitle(draft.content().getMasterTitle(), candidate.getMasterTitle())) {
                mergeInto(domain, candidate, draft);
                return candidate;
            }
        }
        return null;
    }

    private void mergeInto(Domain domain, Content existing, DraftAssembler.IngestDraft draft) {
        throw new UnsupportedOperationException("Task 7"); // 다음 태스크에서 구현
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
