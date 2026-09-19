package com.example.AOD.recommend.notinterested;

import com.example.AOD.recommend.context.RecContextHolder;
import com.example.AOD.recommend.log.RecEventRecorder;
import com.example.AOD.recommend.reaction.ContentNotFoundException;
import com.example.shared.repository.ContentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 관심 없음 (REC_TAB_DESIGN §2-4).
 * 싫어요와 다른 신호다 — 추천 탭에서 90일 동안 **제외만** 하고 감점·시리즈 제외는 하지 않는다.
 *
 * 서빙 상태라 주 데이터소스의 JdbcTemplate 을 쓴다(로그 풀이 아니다).
 * 상태가 실제로 바뀔 때만 이벤트를 남긴다 — 멱등 PUT 을 연타해도 이벤트가 불어나지 않는다.
 * 90일 지난 행의 실제 삭제는 PartitionMaintenanceJob 이 하루 한 번 한다.
 */
@Service
public class NotInterestedService {

    public static final Duration TTL = Duration.ofDays(90);
    /** 라우터에 보낼 제외 목록의 현실적 상한. 이보다 많이 눌렀으면 최근 것부터 쓴다. */
    public static final int MAX_ACTIVE = 3_000;

    static final String UPSERT_SQL =
            "INSERT INTO aod_rec.not_interested (user_id, content_id, created_at) VALUES (?, ?, ?) "
          + "ON CONFLICT (user_id, content_id) DO NOTHING";
    static final String DELETE_SQL =
            "DELETE FROM aod_rec.not_interested WHERE user_id = ? AND content_id = ?";
    /**
     * 최신순 + 상한. 라우터는 플랫폼별 제외 개수에 상한이 있어 넘치면 잘라야 하는데,
     * 순서가 없으면 무엇을 버릴지 정할 수 없고 요청마다 목록이 달라진다.
     */
    static final String ACTIVE_SQL =
            "SELECT content_id FROM aod_rec.not_interested WHERE user_id = ? AND created_at >= ? "
          + "ORDER BY created_at DESC LIMIT " + MAX_ACTIVE;

    private final JdbcTemplate jdbc;
    private final ContentRepository contentRepository;
    private final RecEventRecorder recorder;
    private final Clock clock;

    @Autowired
    public NotInterestedService(JdbcTemplate jdbc, ContentRepository contentRepository, RecEventRecorder recorder) {
        this(jdbc, contentRepository, recorder, Clock.systemUTC());
    }

    NotInterestedService(JdbcTemplate jdbc, ContentRepository contentRepository,
                         RecEventRecorder recorder, Clock clock) {
        this.jdbc = jdbc;
        this.contentRepository = contentRepository;
        this.recorder = recorder;
        this.clock = clock;
    }

    /** 켜기. 돌려주는 값은 갱신 뒤 상태(항상 true — 멱등). */
    @Transactional
    public boolean turnOn(Long userId, Long contentId) {
        requireContent(contentId);
        int changed = jdbc.update(UPSERT_SQL, userId, contentId, OffsetDateTime.now(clock));
        if (changed > 0) recorder.notInterestedChanged(userId, contentId, true, RecContextHolder.current());
        return true;
    }

    /** 끄기(되돌리기). 돌려주는 값은 갱신 뒤 상태(항상 false — 멱등). */
    @Transactional
    public boolean turnOff(Long userId, Long contentId) {
        requireContent(contentId);
        int changed = jdbc.update(DELETE_SQL, userId, contentId);
        if (changed > 0) recorder.notInterestedChanged(userId, contentId, false, RecContextHolder.current());
        return false;
    }

    /** 최근 90일 안의 관심 없음 content_id — 라우터 excluded 로 나간다. */
    public List<Long> activeContentIds(Long userId) {
        return jdbc.queryForList(ACTIVE_SQL, Long.class, userId, OffsetDateTime.now(clock).minus(TTL));
    }

    private void requireContent(Long contentId) {
        if (!contentRepository.existsById(contentId)) throw new ContentNotFoundException(contentId);
    }
}
