package com.example.AOD.user.service;

import com.example.AOD.recommend.auth.RecAuth;
import com.example.AOD.recommend.log.RecLogJdbc;
import com.example.AOD.user.model.User;
import com.example.AOD.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 회원 탈퇴. 리뷰는 지우지 않고 작성자만 비운다.
 * 행동 로그는 그 회원의 행과 함께, **그 회원과 같이 쓰인 익명 ID·세션 ID 의 비로그인 행**도 지운다 —
 * 익명 행이 남으면 로그인 전후 기록이 다시 이어진다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WithdrawService {

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final RecLogJdbc recLogJdbc;
    private final UserRepository userRepository;
    private final RecAuth recAuth;

    /**
     * 로그를 먼저 지운다 — 실패하면 계정이 그대로라 다시 시도할 수 있다. 반대 순서면 계정이 사라진 뒤라
     * 어떤 익명 ID 가 그 회원 것이었는지 찾을 수 없다.
     */
    public void withdraw(User user) {
        Long userId = user.getId();
        deleteLogs(userId);
        tx.executeWithoutResult(status -> deleteAccount(userId));
        recAuth.forget(user.getUsername());
        log.info("회원 탈퇴 처리 완료 (userId={})", userId);
    }

    // ponytail: 요청 안에서 바로 지운다. anon_id 조건은 인덱스가 없어 파티션을 훑는다 —
    // 로그가 커져 느려지면 비동기 작업으로 옮기고 (anon_id) 인덱스를 둔다.
    // 탈퇴 직전에 큐에 들어가 아직 적재되지 않은 로그 몇 건은 이 삭제 뒤에 쓰일 수 있다(보관 기간이 지나면 사라진다).
    private void deleteLogs(Long userId) {
        JdbcTemplate logJdbc = recLogJdbc.jdbc();
        recLogJdbc.tx().executeWithoutResult(status -> {
            String anonIds = uuidArray(logJdbc.queryForList(
                    "SELECT anon_id FROM aod_log.rec_request WHERE user_id = ? "
                  + "UNION SELECT anon_id FROM aod_log.event WHERE user_id = ? AND anon_id IS NOT NULL",
                    UUID.class, userId, userId));
            String sessionIds = uuidArray(logJdbc.queryForList(
                    "SELECT session_id FROM aod_log.rec_request WHERE user_id = ? "
                  + "UNION SELECT session_id FROM aod_log.event WHERE user_id = ? AND session_id IS NOT NULL",
                    UUID.class, userId, userId));

            // 다른 회원의 행(user_id 가 다른 값)은 같은 브라우저를 썼어도 건드리지 않는다.
            logJdbc.update("DELETE FROM aod_log.rec_item_served s USING aod_log.rec_request r "
                         + "WHERE s.request_id = r.request_id "
                         + "AND (r.user_id = ? OR (r.user_id IS NULL AND r.anon_id = ANY(CAST(? AS uuid[]))))",
                    userId, anonIds);
            logJdbc.update("DELETE FROM aod_log.rec_request "
                         + "WHERE user_id = ? OR (user_id IS NULL AND anon_id = ANY(CAST(? AS uuid[])))",
                    userId, anonIds);
            logJdbc.update("DELETE FROM aod_log.event WHERE user_id = ? OR (user_id IS NULL AND "
                         + "(anon_id = ANY(CAST(? AS uuid[])) OR session_id = ANY(CAST(? AS uuid[]))))",
                    userId, anonIds, sessionIds);
            logJdbc.update("DELETE FROM aod_log.client_agent WHERE session_id = ANY(CAST(? AS uuid[]))", sessionIds);
        });
    }

    private void deleteAccount(Long userId) {
        jdbc.update("UPDATE reviews SET user_id = NULL WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM content_likes WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM bookmarks WHERE user_id = ?", userId);

        // 남의 컬렉션에 누른 좋아요 — 그 컬렉션의 좋아요 수도 같이 내린다.
        jdbc.update("UPDATE collections SET like_count = GREATEST(like_count - 1, 0) "
                  + "WHERE id IN (SELECT collection_id FROM collection_likes WHERE user_id = ?)", userId);
        jdbc.update("DELETE FROM collection_likes WHERE user_id = ?", userId);

        // 내 컬렉션 — FK 의 ON DELETE CASCADE 에 기대지 않고 항목·받은 좋아요를 먼저 지운다.
        jdbc.update("DELETE FROM collection_likes WHERE collection_id IN (SELECT id FROM collections WHERE user_id = ?)", userId);
        jdbc.update("DELETE FROM collection_items WHERE collection_id IN (SELECT id FROM collections WHERE user_id = ?)", userId);
        jdbc.update("DELETE FROM collections WHERE user_id = ?", userId);

        jdbc.update("DELETE FROM aod_rec.not_interested WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM aod_rec.rec_chain WHERE user_id = ?", userId);

        userRepository.deleteById(userId);   // roles 테이블까지 JPA 가 지운다
        userRepository.flush();
    }

    /** uuid[] 리터럴 — UUID 는 이스케이프할 문자가 없다. */
    private static String uuidArray(List<UUID> ids) {
        return ids.stream().map(UUID::toString).collect(Collectors.joining(",", "{", "}"));
    }
}
