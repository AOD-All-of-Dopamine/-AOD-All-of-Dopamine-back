package com.example.AOD.recommend.flag;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecFeatureFlagTest {

    @Test
    void enabledWithEmptyAllowListAllowsEveryone() {
        RecFeatureFlag flag = new RecFeatureFlag(true, "");

        assertTrue(flag.allows("tester"));
        assertTrue(flag.allows("anyone"));
    }

    @Test
    void nullOrBlankAllowListIsTreatedAsEmpty() {
        assertTrue(new RecFeatureFlag(true, null).allows("tester"));
        assertTrue(new RecFeatureFlag(true, "   ").allows("tester"));
        assertTrue(new RecFeatureFlag(true, " , ,").allows("tester"));
    }

    @Test
    void killSwitchBeatsTheAllowList() {
        RecFeatureFlag flag = new RecFeatureFlag(false, "tester");

        assertFalse(flag.allows("tester"));
        assertFalse(flag.allows(null));
    }

    @Test
    void allowListIsTrimmedAndExact() {
        RecFeatureFlag flag = new RecFeatureFlag(true, " tester , leader ");

        assertTrue(flag.allows("tester"));
        assertTrue(flag.allows("leader"));
        assertFalse(flag.allows("TESTER"), "대소문자는 구분한다 — username 이 그렇다");
        assertFalse(flag.allows("other"));
        assertFalse(flag.allows(null));
    }
}
