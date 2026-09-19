package com.example.AOD.recommend.key;

import com.example.shared.entity.Content;
import com.example.shared.entity.PlatformData;
import com.example.shared.repository.PlatformDataRepository;
import com.example.shared.repository.PlatformKeyRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * content_id ↔ 라우터 키 일괄 변환 (설계 §3).
 * 한 작품에 지원 플랫폼 행이 여럿이면 platform_data_id 가 가장 작은 행을 쓴다 —
 * 같은 사용자가 같은 요청을 다시 보내도 같은 키가 나가야 추천이 흔들리지 않는다.
 */
@Service
@RequiredArgsConstructor
public class CorpusKeyService {

    private final PlatformDataRepository platformDataRepository;

    /** 정방향. 쿼리 1개. 매핑이 없는 작품은 결과에 없다(= dropped_seed_ids 후보). */
    public Map<Long, CorpusKey> keysByContentId(Collection<Long> contentIds) {
        if (contentIds == null || contentIds.isEmpty()) return Map.of();
        List<Long> ids = new ArrayList<>(new LinkedHashSet<>(contentIds));
        Map<Long, CorpusKey> out = new LinkedHashMap<>();
        Map<Long, Long> chosenRowId = new HashMap<>();
        for (PlatformKeyRow row : platformDataRepository.findRecKeyRowsByContentIds(
                ids, CorpusKeyMapper.SUPPORTED_PLATFORM_NAMES)) {
            CorpusKey key = CorpusKeyMapper.toKey(row.platformName(), row.platformSpecificId());
            if (key == null) continue;
            Long chosen = chosenRowId.get(row.contentId());
            if (chosen != null && chosen <= row.platformDataId()) continue;
            chosenRowId.put(row.contentId(), row.platformDataId());
            out.put(row.contentId(), key);
        }
        return out;
    }

    /** 역방향. 플랫폼 이름별로 쿼리 1개(최대 5개). 성인 여부는 여기서 거르지 않는다 — 호출자가 이유와 함께 버린다. */
    public Map<CorpusKey, Content> contentsByKey(Collection<CorpusKey> keys) {
        if (keys == null || keys.isEmpty()) return Map.of();
        Map<String, Set<String>> idsByPlatformName = new LinkedHashMap<>();
        for (CorpusKey key : keys) {
            CorpusKeyMapper.PlatformRef ref = CorpusKeyMapper.toPlatformRef(key);
            if (ref == null) continue;
            idsByPlatformName.computeIfAbsent(ref.platformName(), name -> new LinkedHashSet<>())
                    .add(ref.platformSpecificId());
        }
        Map<CorpusKey, Content> out = new LinkedHashMap<>();
        for (Map.Entry<String, Set<String>> entry : idsByPlatformName.entrySet()) {
            for (PlatformData pd : platformDataRepository.findWithContentByPlatformNameAndIds(
                    entry.getKey(), entry.getValue())) {
                CorpusKey key = CorpusKeyMapper.toKey(pd.getPlatformName(), pd.getPlatformSpecificId());
                if (key != null && pd.getContent() != null) out.putIfAbsent(key, pd.getContent());
            }
        }
        return out;
    }
}
