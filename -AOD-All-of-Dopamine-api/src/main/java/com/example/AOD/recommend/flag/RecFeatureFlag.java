package com.example.AOD.recommend.flag;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 추천 API 킬 스위치 (REC_TAB_DESIGN §6-2).
 * rec.enabled=false 거나 rec.allowed-users 목록 밖이면 추천을 계산하지 않고 disabled 대체로 답한다 —
 * 소수 공개(내부 테스트) 단계에서 화면을 깨지 않고 범위를 좁히는 수단이다.
 */
@Component
public class RecFeatureFlag {

    private final boolean enabled;
    private final Set<String> allowedUsers;

    public RecFeatureFlag(@Value("${rec.enabled:true}") boolean enabled,
                          @Value("${rec.allowed-users:}") String allowedUsers) {
        this.enabled = enabled;
        this.allowedUsers = allowedUsers == null ? Set.of()
                : Arrays.stream(allowedUsers.split(","))
                        .map(String::trim)
                        .filter(name -> !name.isEmpty())
                        .collect(Collectors.toUnmodifiableSet());
    }

    /** 허용 목록이 비어 있으면 전체 허용. */
    public boolean allows(String username) {
        if (!enabled) return false;
        return allowedUsers.isEmpty() || (username != null && allowedUsers.contains(username));
    }
}
