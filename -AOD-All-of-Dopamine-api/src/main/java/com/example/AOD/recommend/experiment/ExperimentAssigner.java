package com.example.AOD.recommend.experiment;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;

/**
 * 실험 배정 — 지금은 칸만 만든다 (REC_TAB_DESIGN §5-5).
 * 추천은 로그인 사용자만 받으므로 user_id 로만 배정한다. 레이어마다 salt 를 다르게 둬서 레이어끼리 직교시킨다.
 * JDK MessageDigest 로 충분하다 — 이 레포에 Guava 가 없고 도입하지 않는다.
 *
 * 응답을 **실제로 만든** 변형을 rec_request.experiments 에 남긴다(배정이 아니라 적용 기준).
 * 대체 응답은 assign 을 부르지 않거나 userId=null 이라 빈 객체가 된다.
 */
@Component
public class ExperimentAssigner {

    public static final String LAYER_REC_RANKER = "rec_ranker";
    static final String SALT_REC_RANKER = "rec_ranker_v1";
    static final int BUCKETS = 10_000;

    /** SHA-256("{layer}:{salt}:{userId}") 앞 4바이트를 부호 없는 정수로 % 10000. */
    public static int bucket(String layer, String salt, long userId) {
        byte[] hash = sha256(layer + ":" + salt + ":" + userId);
        long head = ((long) (hash[0] & 0xFF) << 24)
                  | ((long) (hash[1] & 0xFF) << 16)
                  | ((long) (hash[2] & 0xFF) << 8)
                  | ((long) (hash[3] & 0xFF));
        return (int) (head % BUCKETS);
    }

    /** 로그인 사용자의 적용 변형. userId 가 없으면(익명·대체) 빈 객체. */
    public Map<String, String> assign(Long userId) {
        if (userId == null) return Map.of();
        return Map.of(LAYER_REC_RANKER, variantOf(bucket(LAYER_REC_RANKER, SALT_REC_RANKER, userId)));
    }

    /** 변형이 하나뿐이라 모든 칸이 control 이다. 변형을 늘릴 때 이 함수만 고친다. */
    static String variantOf(int bucket) {
        return "control";
    }

    private static byte[] sha256(String input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 이 없는 JVM 은 없다", e);
        }
    }
}
