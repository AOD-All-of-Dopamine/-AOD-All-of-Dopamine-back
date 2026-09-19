package com.example.AOD.recommend.card;

import com.example.AOD.recommend.key.CorpusKey;
import com.example.AOD.recommend.key.CorpusKeyService;
import com.example.AOD.recommend.router.RouterText;
import com.example.AOD.recommend.router.dto.RouterItem;
import com.example.shared.entity.Content;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 라우터 항목 → 카드 (REC_TAB_DESIGN §4-2 · 설계 §5).
 * 라우터 순서를 그대로 지키며 size 장을 채우고, 버린 후보는 이유와 함께 남긴다.
 * 키 → Content 는 플랫폼별 1쿼리(CorpusKeyService)로 한꺼번에 읽는다 — N+1 을 만들지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CardAssembler {

    private final CorpusKeyService corpusKeyService;

    /**
     * @param items          라우터가 준 후보 (순서 유지)
     * @param size           채울 카드 수
     * @param usedContentIds 이미 쓴 작품 (체인 seen + 같은 요청의 앞선 호출분)
     */
    public Assembly assemble(List<RouterItem> items, int size, Set<Long> usedContentIds) {
        List<AssembledCard> cards = new ArrayList<>();
        List<DroppedCandidate> dropped = new ArrayList<>();
        if (items == null || items.isEmpty()) return new Assembly(cards, dropped);

        List<RouterItem> usable = usable(items);
        if (usable.isEmpty()) return new Assembly(cards, dropped);

        Set<CorpusKey> keys = new LinkedHashSet<>();
        for (RouterItem item : usable) keys.add(new CorpusKey(item.platform(), item.key()));
        Map<CorpusKey, Content> contentByKey = corpusKeyService.contentsByKey(keys);

        Set<Long> used = new HashSet<>(usedContentIds == null ? Set.of() : usedContentIds);
        for (RouterItem item : usable) {
            Content content = contentByKey.get(new CorpusKey(item.platform(), item.key()));
            if (content == null) {
                dropped.add(new DroppedCandidate(item, null, DroppedCandidate.NOT_IN_DB));
                continue;
            }
            if (Boolean.TRUE.equals(content.getIsAdult())) {
                dropped.add(new DroppedCandidate(item, content.getContentId(), DroppedCandidate.ADULT));
                continue;
            }
            // 정원 판정이 중복 판정보다 먼저다 — 아니면 정원 밖 후보가 "본 것"으로 표시돼 다음 쪽에서 사라진다.
            if (cards.size() >= size) {
                dropped.add(new DroppedCandidate(item, content.getContentId(), DroppedCandidate.OVER_K));
                continue;
            }
            if (!used.add(content.getContentId())) {
                dropped.add(new DroppedCandidate(item, content.getContentId(), DroppedCandidate.DUP_CONTENT));
                continue;
            }
            cards.add(new AssembledCard(item, content));
        }
        return new Assembly(cards, dropped);
    }

    /**
     * 저장소에 넣을 수 없는 키(제어 문자·과도한 길이)를 가진 후보는 **버린 후보로도 남기지 않는다** —
     * 남기면 그 키가 rec_item_served 와 rec_chain.skipped_keys 로 흘러가 로그 배치와 체인 갱신을 깨뜨린다.
     * 정상 라우터라면 0건이라 한 번만 경고한다.
     */
    private static List<RouterItem> usable(List<RouterItem> items) {
        List<RouterItem> out = new ArrayList<>(items.size());
        int rejected = 0;
        for (RouterItem item : items) {
            if (item == null || !RouterText.isSafe(item.platform()) || !RouterText.isSafe(item.key())) {
                rejected++;
                continue;
            }
            out.add(item);
        }
        if (rejected > 0) log.warn("라우터 후보 {}건의 platform·key 를 쓸 수 없어 버렸다(제어 문자 또는 길이 초과)", rejected);
        return out;
    }
}
