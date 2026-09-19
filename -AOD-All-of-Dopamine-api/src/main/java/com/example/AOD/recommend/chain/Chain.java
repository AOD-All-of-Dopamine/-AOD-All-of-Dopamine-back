package com.example.AOD.recommend.chain;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** aod_rec.rec_chain 1행. 한 칩(tab)에서 이어 보는 추천 흐름의 상태 (REC_TAB_DESIGN §2-6). */
public record Chain(UUID chainId, Long userId, String tab, List<Long> seenIds,
                    List<String> skippedKeys, int pageDepth, OffsetDateTime updatedAt) { }
