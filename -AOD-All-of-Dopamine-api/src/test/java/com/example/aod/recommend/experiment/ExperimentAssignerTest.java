package com.example.AOD.recommend.experiment;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExperimentAssignerTest {

    private final ExperimentAssigner assigner = new ExperimentAssigner();

    @Test
    void bucketIsStableAndInRange() {
        int first = ExperimentAssigner.bucket("rec_ranker", "salt", 7L);
        int second = ExperimentAssigner.bucket("rec_ranker", "salt", 7L);

        assertEquals(first, second, "같은 입력이면 항상 같은 칸");
        assertTrue(first >= 0 && first < 10_000, "0~9999 범위: " + first);
    }

    @Test
    void differentSaltsMakeLayersOrthogonal() {
        // 레이어마다 salt 를 다르게 둬 레이어끼리 상관되지 않게 한다 (REC_TAB_DESIGN §5-5).
        assertNotEquals(ExperimentAssigner.bucket("a", "salt1", 7L), ExperimentAssigner.bucket("a", "salt2", 7L));
        assertNotEquals(ExperimentAssigner.bucket("a", "salt", 7L), ExperimentAssigner.bucket("b", "salt", 7L));
    }

    @Test
    void bucketsSpreadAcrossTheRange() {
        Set<Integer> hundreds = new HashSet<>();
        for (long userId = 1; userId <= 300; userId++) {
            hundreds.add(ExperimentAssigner.bucket("rec_ranker", "salt", userId) / 1_000);
        }
        assertTrue(hundreds.size() >= 8, "10칸 중 최소 8칸에 흩어져야 한다 — 실제: " + hundreds.size());
    }

    @Test
    void everyUserIsControlForNow() {
        assertEquals(Map.of("rec_ranker", "control"), assigner.assign(7L));
        assertEquals(Map.of("rec_ranker", "control"), assigner.assign(123_456L));
    }

    @Test
    void anonymousOrFallbackHasNoExperiments() {
        assertEquals(Map.of(), assigner.assign(null));
    }
}
