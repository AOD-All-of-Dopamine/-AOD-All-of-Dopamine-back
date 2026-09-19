package com.example.AOD.recommend.chain;

import com.example.AOD.recommend.log.SqlArrays;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.sql.Array;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * aod_rec.rec_chain 의 유일한 쓰기·읽기 경로 (REC_TAB_DESIGN §2-6 · 설계 §5).
 *
 * 서빙 상태라 **주 데이터소스**의 JdbcTemplate 을 쓴다 — RecLogJdbc(연결 2개)는 유실을 허용하는
 * 로그 전용이다. 여기서 JdbcTemplate 빈을 새로 만들지 않는다(Boot 의 @Primary JdbcTemplate 이
 * 사라지면 기존 주입 지점이 전부 로그 풀에 붙는다 — RecLogJdbc 주석 참고).
 *
 * 배열은 CAST(? AS bigint[]) + 리터럴 문자열로 넘긴다(SqlArrays).
 */
@Slf4j
@Service
public class ChainService {

    /** seen 이 이 수에 닿으면 hasMore=false (REC_TAB_DESIGN §2-6, 25페이지). */
    public static final int SEEN_MAX = 500;
    /** 버린 후보 키 상한 (설계 §4). */
    public static final int SKIPPED_MAX = 2_000;
    /**
     * 체인 수명. **마지막 활동 기준으로 미끄러진다** — updated_at 이 갱신되므로 계속 "더 보기"를 누르는 동안은
     * 만료되지 않고, 24시간 동안 손대지 않은 체인만 사라진다(조회에서 제외 + 일일 정리에서 삭제).
     */
    public static final Duration TTL = Duration.ofHours(24);

    static final String FIND_SQL =
            "SELECT chain_id, user_id, tab, seen_ids, skipped_keys, page_depth, updated_at "
          + "FROM aod_rec.rec_chain WHERE chain_id = ?";
    static final String INSERT_SQL =
            "INSERT INTO aod_rec.rec_chain (chain_id, user_id, tab, seen_ids, skipped_keys, page_depth, updated_at) "
          + "VALUES (?, ?, ?, CAST(? AS bigint[]), CAST(? AS text[]), ?, ?)";
    /**
     * 이번 응답분만 넘기고 합치기·중복 제거·정원·page_depth 는 **DB 안에서** 끝낸다.
     * 요청 시작 때 읽은 스냅숏으로 통째로 덮어쓰면 "더 보기" 연타에서 앞 요청이 남긴 seen 이 사라진다
     * (lost update). RETURNING 으로 합쳐진 실제 상태를 받아 응답의 pageDepth·hasMore 에 쓴다.
     *
     * 안쪽 SELECT: (기존 || 새것)을 순서 번호와 함께 풀어 값별 첫 등장 번호(min)로 묶고 —
     * 중복은 처음 본 자리를 지킨다 — 번호가 큰(=최근) 것부터 정원만큼 남긴 뒤 다시 순서대로 모은다.
     */
    static final String UPDATE_SQL =
            "UPDATE aod_rec.rec_chain SET "
          + "seen_ids = (SELECT COALESCE(array_agg(v ORDER BY first_ord), '{}'::bigint[]) FROM ("
          + "  SELECT v, min(ord) AS first_ord"
          + "  FROM unnest(seen_ids || CAST(? AS bigint[])) WITH ORDINALITY AS u(v, ord)"
          + "  GROUP BY v ORDER BY first_ord DESC LIMIT " + SEEN_MAX + ") kept), "
          + "skipped_keys = (SELECT COALESCE(array_agg(v ORDER BY first_ord), '{}'::text[]) FROM ("
          + "  SELECT v, min(ord) AS first_ord"
          + "  FROM unnest(skipped_keys || CAST(? AS text[])) WITH ORDINALITY AS u(v, ord)"
          + "  GROUP BY v ORDER BY first_ord DESC LIMIT " + SKIPPED_MAX + ") kept), "
          + "page_depth = page_depth + 1, updated_at = ? "
          + "WHERE chain_id = ? "
          + "RETURNING chain_id, user_id, tab, seen_ids, skipped_keys, page_depth, updated_at";
    static final String PURGE_SQL = "DELETE FROM aod_rec.rec_chain WHERE updated_at < ?";

    static final RowMapper<Chain> ROW_MAPPER = (rs, rowNum) -> new Chain(
            (UUID) rs.getObject("chain_id"),
            rs.getLong("user_id"),
            rs.getString("tab"),
            longs(rs.getArray("seen_ids")),
            texts(rs.getArray("skipped_keys")),
            rs.getInt("page_depth"),
            rs.getObject("updated_at", OffsetDateTime.class));

    private final JdbcTemplate jdbc;
    private final Clock clock;

    @Autowired
    public ChainService(JdbcTemplate jdbc) {
        this(jdbc, Clock.systemUTC());
    }

    ChainService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** 소유자·탭이 맞고 24시간 안이면 체인. 그 밖에는 비어 있다 — 호출자는 404 로 답한다. */
    public Optional<Chain> find(UUID chainId, Long userId, String tab) {
        if (chainId == null) return Optional.empty();
        List<Chain> rows = jdbc.query(FIND_SQL, ROW_MAPPER, chainId);
        if (rows.isEmpty()) return Optional.empty();
        Chain chain = rows.get(0);
        if (!Objects.equals(chain.userId(), userId)) return Optional.empty();
        if (!Objects.equals(chain.tab(), tab)) return Optional.empty();
        if (chain.updatedAt() == null
                || chain.updatedAt().isBefore(OffsetDateTime.now(clock).minus(TTL))) {
            return Optional.empty();
        }
        return Optional.of(chain);
    }

    /** 새 체인 (page_depth=0). 대체 응답은 이 메서드를 부르지 않는다 — 체인을 만들지 않는다. */
    public Chain create(UUID chainId, Long userId, String tab, List<Long> seenIds, List<String> skippedKeys) {
        List<Long> seen = capNewest(distinct(seenIds), SEEN_MAX);
        List<String> skipped = capNewest(distinct(skippedKeys), SKIPPED_MAX);
        OffsetDateTime now = OffsetDateTime.now(clock);
        jdbc.update(INSERT_SQL, chainId, userId, tab, SqlArrays.bigints(seen), SqlArrays.texts(skipped), 0, now);
        return new Chain(chainId, userId, tab, seen, skipped, 0, now);
    }

    /**
     * 기존 체인에 이번 응답분을 덧붙이고 page_depth 를 1 올린다(합치기는 DB 안에서 — UPDATE_SQL 참고).
     * @return 갱신된 실제 행. 그사이 정리 작업이 체인을 지웠으면 null — 호출자는 이어 보기를 끈다.
     */
    public Chain append(Chain chain, List<Long> newSeenIds, List<String> newSkippedKeys) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        List<Chain> updated = jdbc.query(UPDATE_SQL, ROW_MAPPER,
                SqlArrays.bigints(distinct(newSeenIds)), SqlArrays.texts(distinct(newSkippedKeys)),
                now, chain.chainId());
        if (updated.isEmpty()) {
            log.warn("갱신할 체인이 없다 — 만료 정리와 겹쳤다 (chainId={})", chain.chainId());
            return null;
        }
        return updated.get(0);
    }

    /** 24시간 지난 체인 삭제. PartitionMaintenanceJob 이 하루 한 번 부른다. */
    public int purgeExpired() {
        return jdbc.update(PURGE_SQL, OffsetDateTime.now(clock).minus(TTL));
    }

    private static <T> List<T> distinct(List<T> values) {
        return values == null ? List.of() : new ArrayList<>(new LinkedHashSet<>(values));
    }

    /** 상한을 넘으면 오래된 앞쪽을 버린다(뒤쪽이 최근). */
    private static <T> List<T> capNewest(List<T> values, int max) {
        return values.size() <= max ? values : new ArrayList<>(values.subList(values.size() - max, values.size()));
    }

    private static List<Long> longs(Array array) throws SQLException {
        if (array == null) return List.of();
        Object raw = array.getArray();
        if (!(raw instanceof Object[] values)) return List.of();
        List<Long> out = new ArrayList<>(values.length);
        for (Object value : values) {
            if (value instanceof Number number) out.add(number.longValue());
        }
        return out;
    }

    private static List<String> texts(Array array) throws SQLException {
        if (array == null) return List.of();
        Object raw = array.getArray();
        if (!(raw instanceof Object[] values)) return List.of();
        List<String> out = new ArrayList<>(values.length);
        for (Object value : values) {
            if (value != null) out.add(value.toString());
        }
        return out;
    }
}
