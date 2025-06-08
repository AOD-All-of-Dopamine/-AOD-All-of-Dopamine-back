package com.example.AOD.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "http://localhost:3000")
public class MoviesController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // ===== 페이징 응답 클래스 =====
    public static class PaginatedResponse {
        private List<Map<String, Object>> items;
        private int currentPage;
        private int totalPages;
        private long total;
        private int limit;
        private boolean hasNext;
        private boolean hasPrevious;

        public PaginatedResponse(List<Map<String, Object>> items, int currentPage,
                                 int totalPages, long total, int limit) {
            this.items = items;
            this.currentPage = currentPage;
            this.totalPages = totalPages;
            this.total = total;
            this.limit = limit;
            this.hasNext = currentPage < totalPages;
            this.hasPrevious = currentPage > 1;
        }

        // Getters and Setters
        public List<Map<String, Object>> getItems() { return items; }
        public void setItems(List<Map<String, Object>> items) { this.items = items; }
        public int getCurrentPage() { return currentPage; }
        public void setCurrentPage(int currentPage) { this.currentPage = currentPage; }
        public int getTotalPages() { return totalPages; }
        public void setTotalPages(int totalPages) { this.totalPages = totalPages; }
        public long getTotal() { return total; }
        public void setTotal(long total) { this.total = total; }
        public int getLimit() { return limit; }
        public void setLimit(int limit) { this.limit = limit; }
        public boolean isHasNext() { return hasNext; }
        public void setHasNext(boolean hasNext) { this.hasNext = hasNext; }
        public boolean isHasPrevious() { return hasPrevious; }
        public void setHasPrevious(boolean hasPrevious) { this.hasPrevious = hasPrevious; }
    }

    // ===== 헬퍼 메서드들 =====
    private String buildOrderClause(String sort, String order, String contentType) {
        String direction = "desc".equalsIgnoreCase(order) ? "DESC" : "ASC";

        switch (sort.toLowerCase()) {
            case "rating":
                // 웹소설, 웹툰, 게임, OTT는 rating 컬럼이 없으므로 다른 컬럼으로 대체
                if ("novel".equals(contentType) || "webtoon".equals(contentType) || "game".equals(contentType) || "ott".equals(contentType)) {
                    return "ORDER BY created_at " + direction + " NULLS LAST ";
                }
                return "ORDER BY rating " + direction + " NULLS LAST ";
            case "latest":
                if ("webtoon".equals(contentType)) {
                    return "ORDER BY publish_date " + direction + " NULLS LAST ";
                } else if ("movie".equals(contentType) || "ott".equals(contentType)) {
                    return "ORDER BY COALESCE(release_date, release_year, created_at) " + direction + " NULLS LAST ";
                }
                return "ORDER BY COALESCE(release_date, publish_date, created_at) " + direction + " NULLS LAST ";
            case "title":
                return "ORDER BY title " + direction + " ";
            case "popular":
                if ("novel".equals(contentType) || "webtoon".equals(contentType)) {
                    return "ORDER BY created_at " + direction + " NULLS LAST ";
                } else if ("game".equals(contentType)) {
                    return "ORDER BY COALESCE(final_price, 999999) ASC, created_at " + direction + " NULLS LAST ";
                } else if ("ott".equals(contentType)) {
                    return "ORDER BY release_year " + direction + " NULLS LAST ";
                }
                return "ORDER BY COALESCE(total_audience, rating, 0) " + direction + " NULLS LAST ";
            case "year":
                return "ORDER BY COALESCE(release_year, EXTRACT(YEAR FROM release_date)) " + direction + " NULLS LAST ";
            case "price":
                return "ORDER BY COALESCE(final_price, 0) " + direction + " ";
            default:
                return "ORDER BY id " + direction + " ";
        }
    }

    private String buildWhereClause(String search, String genre, String rating, String contentType) {
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");

        if (search != null && !search.trim().isEmpty()) {
            switch (contentType) {
                case "movie":
                    where.append(" AND (title LIKE ? OR summary LIKE ? OR director LIKE ?)");
                    break;
                case "ott":
                    where.append(" AND (title LIKE ? OR description LIKE ? OR creator LIKE ?)");
                    break;
                case "game":
                case "novel":
                case "webtoon":
                    where.append(" AND (title LIKE ? OR summary LIKE ?)");
                    break;
            }
        }

        if (genre != null && !genre.equals("all")) {
            where.append(" AND id IN (SELECT ").append(getGenreTableInfo(contentType)[1])
                    .append(" FROM ").append(getGenreTableInfo(contentType)[0])
                    .append(" WHERE genre = ?)");
        }

        if (rating != null && !rating.equals("all")) {
            double minRating = parseRatingFilter(rating);
            where.append(" AND rating >= ").append(minRating);
        }

        return where.toString();
    }

    private String[] getGenreTableInfo(String contentType) {
        switch (contentType) {
            case "movie": return new String[]{"movie_common_genre", "movie_id"};
            case "ott": return new String[]{"ott_common_genre", "ott_id"};
            case "novel": return new String[]{"novel_common_genre", "novel_id"};
            case "webtoon": return new String[]{"webtoon_common_genre", "webtoon_id"};
            default: return new String[]{"", ""};
        }
    }

    private double parseRatingFilter(String rating) {
        switch (rating) {
            case "9+": return 9.0;
            case "8+": return 8.0;
            case "7+": return 7.0;
            case "6+": return 6.0;
            default: return 0.0;
        }
    }

    // ===== 파라미터 빌더 헬퍼 메서드 =====
    private Object[] buildParams(String search, String genre, String contentType) {
        List<Object> params = new ArrayList<>();

        // 검색어 파라미터 추가
        if (search != null && !search.trim().isEmpty()) {
            String searchPattern = "%" + search + "%";
            switch (contentType) {
                case "movie":
                case "ott":
                    // title, summary/description, director/creator 3개
                    params.add(searchPattern);
                    params.add(searchPattern);
                    params.add(searchPattern);
                    break;
                case "game":
                case "novel":
                case "webtoon":
                    // title, summary 2개
                    params.add(searchPattern);
                    params.add(searchPattern);
                    break;
            }
        }

        // 장르 파라미터 추가
        if (genre != null && !genre.equals("all")) {
            params.add(genre);
        }

        return params.toArray();
    }

    // ===== 영화 API =====
    @GetMapping("/movies")
    public ResponseEntity<List<Map<String, Object>>> getMovies() {
        try {
            String sql = """
                SELECT mc.*, 
                       STRING_AGG(DISTINCT mca.actor, ', ') as actors,
                       STRING_AGG(DISTINCT mcg.genre, ', ') as genres
                FROM movie_common mc
                LEFT JOIN movie_common_actors mca ON mc.id = mca.movie_id
                LEFT JOIN movie_common_genre mcg ON mc.id = mcg.movie_id
                GROUP BY mc.id, mc.title, mc.summary, mc.director, mc.image_url, mc.release_date, mc.country, mc.age_rating, mc.rating, mc.running_time, mc.total_audience, mc.is_rerelease, mc.reservation_rate, mc.created_at, mc.updated_at, mc.version
                """;
            List<Map<String, Object>> movies = jdbcTemplate.queryForList(sql);
            System.out.println("Movies count: " + movies.size());
            return ResponseEntity.ok(movies);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body(null);
        }
    }

    @GetMapping("/movies/paginated")
    public ResponseEntity<PaginatedResponse> getMoviesPaginated(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "rating") String sort,
            @RequestParam(defaultValue = "desc") String order,
            @RequestParam(required = false) String genre,
            @RequestParam(required = false) String rating,
            @RequestParam(required = false) String search) {

        try {
            int offset = (Math.max(1, page) - 1) * limit;
            String whereClause = buildWhereClause(search, genre, rating, "movie");
            String orderClause = buildOrderClause(sort, order, "movie");

            String countSql = "SELECT COUNT(*) FROM movie_common mc" + whereClause;
            String dataSql = """
            SELECT mc.*, 
                   STRING_AGG(DISTINCT mca.actor, ', ') as actors,
                   STRING_AGG(DISTINCT mcg.genre, ', ') as genres
            FROM movie_common mc
            LEFT JOIN movie_common_actors mca ON mc.id = mca.movie_id
            LEFT JOIN movie_common_genre mcg ON mc.id = mcg.movie_id
            """ + whereClause + """
            GROUP BY mc.id, mc.title, mc.summary, mc.director, mc.image_url, mc.release_date, mc.country, mc.age_rating, mc.rating, mc.running_time, mc.total_audience, mc.is_rerelease, mc.reservation_rate, mc.created_at, mc.updated_at, mc.version
            """ + orderClause + """ 
            LIMIT ? OFFSET ?
            """;

            System.out.println("검색어: " + search);
            System.out.println("장르: " + genre);
            System.out.println("WHERE 절: " + whereClause);

            Object[] params = buildParams(search, genre, "movie");
            Object[] dataParams = new Object[params.length + 2];
            System.arraycopy(params, 0, dataParams, 0, params.length);
            dataParams[params.length] = limit;
            dataParams[params.length + 1] = offset;

            System.out.println("파라미터 개수: " + params.length);
            for (int i = 0; i < params.length; i++) {
                System.out.println("파라미터 " + i + ": " + params[i]);
            }

            long totalCount = jdbcTemplate.queryForObject(countSql, params, Long.class);
            List<Map<String, Object>> movies = jdbcTemplate.queryForList(dataSql, dataParams);
            int totalPages = (int) Math.ceil((double) totalCount / limit);

            PaginatedResponse response = new PaginatedResponse(movies, page, totalPages, totalCount, limit);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("에러 발생: " + e.getMessage());
            return ResponseEntity.badRequest().body(null);
        }
    }

    @GetMapping("/movies/genre/{genreName}")
    public ResponseEntity<List<Map<String, Object>>> getMoviesByGenre(@PathVariable String genreName) {
        try {
            String sql = """
                SELECT mc.*, 
                       STRING_AGG(DISTINCT mca.actor, ', ') as actors,
                       STRING_AGG(DISTINCT mcg.genre, ', ') as genres
                FROM movie_common mc
                LEFT JOIN movie_common_actors mca ON mc.id = mca.movie_id
                LEFT JOIN movie_common_genre mcg ON mc.id = mcg.movie_id
                WHERE mc.id IN (
                    SELECT movie_id FROM movie_common_genre WHERE genre = ?
                )
                GROUP BY mc.id, mc.title, mc.summary, mc.director, mc.image_url, mc.release_date, mc.country, mc.age_rating, mc.rating, mc.running_time, mc.total_audience, mc.is_rerelease, mc.reservation_rate, mc.created_at, mc.updated_at, mc.version
                """;
            List<Map<String, Object>> movies = jdbcTemplate.queryForList(sql, genreName);
            return ResponseEntity.ok(movies);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body(null);
        }
    }

    @GetMapping("/movies/actor/{actorName}")
    public ResponseEntity<List<Map<String, Object>>> getMoviesByActor(@PathVariable String actorName) {
        try {
            String sql = """
                SELECT mc.*, 
                       STRING_AGG(DISTINCT mca.actor, ', ') as actors,
                       STRING_AGG(DISTINCT mcg.genre, ', ') as genres
                FROM movie_common mc
                LEFT JOIN movie_common_actors mca ON mc.id = mca.movie_id
                LEFT JOIN movie_common_genre mcg ON mc.id = mcg.movie_id
                WHERE mc.id IN (
                    SELECT movie_id FROM movie_common_actors WHERE actor = ?
                )
                GROUP BY mc.id, mc.title, mc.summary, mc.director, mc.image_url, mc.release_date, mc.country, mc.age_rating, mc.rating, mc.running_time, mc.total_audience, mc.is_rerelease, mc.reservation_rate, mc.created_at, mc.updated_at, mc.version
                """;
            List<Map<String, Object>> movies = jdbcTemplate.queryForList(sql, actorName);
            return ResponseEntity.ok(movies);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body(null);
        }
    }

    // ===== 게임 API =====
    @GetMapping("/games")
    public ResponseEntity<List<Map<String, Object>>> getGames() {
        try {
            String sql = """
                SELECT gc.*, 
                       STRING_AGG(DISTINCT gcp.publisher, ', ') as publishers,
                       STRING_AGG(DISTINCT gcd.developer, ', ') as developers
                FROM game_common gc
                LEFT JOIN game_common_publisher gcp ON gc.id = gcp.game_id
                LEFT JOIN game_common_developer gcd ON gc.id = gcd.game_id
                GROUP BY gc.id, gc.title, gc.summary, gc.image_url, gc.platform, gc.required_age, gc.final_price, gc.initial_price, gc.created_at, gc.updated_at, gc.version
                """;
            List<Map<String, Object>> games = jdbcTemplate.queryForList(sql);
            System.out.println("Games count: " + games.size());
            return ResponseEntity.ok(games);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body(null);
        }
    }

    @GetMapping("/games/paginated")
    public ResponseEntity<PaginatedResponse> getGamesPaginated(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "popular") String sort,
            @RequestParam(defaultValue = "desc") String order,
            @RequestParam(required = false) String search) {

        try {
            int offset = (Math.max(1, page) - 1) * limit;
            String whereClause = buildWhereClause(search, null, null, "game");
            String orderClause = buildOrderClause(sort, order, "game");

            String countSql = "SELECT COUNT(*) FROM game_common gc" + whereClause;
            String dataSql = """
            SELECT gc.*, 
                   STRING_AGG(DISTINCT gcp.publisher, ', ') as publishers,
                   STRING_AGG(DISTINCT gcd.developer, ', ') as developers
            FROM game_common gc
            LEFT JOIN game_common_publisher gcp ON gc.id = gcp.game_id
            LEFT JOIN game_common_developer gcd ON gc.id = gcd.game_id
            """ + whereClause + """
            GROUP BY gc.id, gc.title, gc.summary, gc.image_url, gc.platform, gc.required_age, gc.final_price, gc.initial_price, gc.created_at, gc.updated_at, gc.version
            """ + orderClause + """
            LIMIT ? OFFSET ?
            """;

            Object[] params = buildParams(search, null, "game");
            Object[] dataParams = new Object[params.length + 2];
            System.arraycopy(params, 0, dataParams, 0, params.length);
            dataParams[params.length] = limit;
            dataParams[params.length + 1] = offset;

            long totalCount = jdbcTemplate.queryForObject(countSql, params, Long.class);
            List<Map<String, Object>> games = jdbcTemplate.queryForList(dataSql, dataParams);
            int totalPages = (int) Math.ceil((double) totalCount / limit);

            PaginatedResponse response = new PaginatedResponse(games, page, totalPages, totalCount, limit);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body(null);
        }
    }

    @GetMapping("/games/publisher/{publisherName}")
    public ResponseEntity<List<Map<String, Object>>> getGamesByPublisher(@PathVariable String publisherName) {
        try {
            String sql = """
                SELECT gc.*, 
                       STRING_AGG(DISTINCT gcp.publisher, ', ') as publishers,
                       STRING_AGG(DISTINCT gcd.developer, ', ') as developers
                FROM game_common gc
                LEFT JOIN game_common_publisher gcp ON gc.id = gcp.game_id
                LEFT JOIN game_common_developer gcd ON gc.id = gcd.game_id
                WHERE gc.id IN (
                    SELECT game_id FROM game_common_publisher WHERE publisher = ?
                )
                GROUP BY gc.id, gc.title, gc.summary, gc.image_url, gc.platform, gc.required_age, gc.final_price, gc.initial_price, gc.created_at, gc.updated_at, gc.version
                """;
            List<Map<String, Object>> games = jdbcTemplate.queryForList(sql, publisherName);
            return ResponseEntity.ok(games);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body(null);
        }
    }

    @GetMapping("/games/developer/{developerName}")
    public ResponseEntity<List<Map<String, Object>>> getGamesByDeveloper(@PathVariable String developerName) {
        try {
            String sql = """
                SELECT gc.*, 
                       STRING_AGG(DISTINCT gcp.publisher, ', ') as publishers,
                       STRING_AGG(DISTINCT gcd.developer, ', ') as developers
                FROM game_common gc
                LEFT JOIN game_common_publisher gcp ON gc.id = gcp.game_id
                LEFT JOIN game_common_developer gcd ON gc.id = gcd.game_id
                WHERE gc.id IN (
                    SELECT game_id FROM game_common_developer WHERE developer = ?
                )
                GROUP BY gc.id, gc.title, gc.summary, gc.image_url, gc.platform, gc.required_age, gc.final_price, gc.initial_price, gc.created_at, gc.updated_at, gc.version
                """;
            List<Map<String, Object>> games = jdbcTemplate.queryForList(sql, developerName);
            return ResponseEntity.ok(games);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body(null);
        }
    }

    // ===== 웹툰 API =====
    @GetMapping("/webtoons")
    public ResponseEntity<List<Map<String, Object>>> getWebtoons() {
        try {
            String sql = """
                SELECT wc.*, 
                       STRING_AGG(DISTINCT wca.author, ', ') as authors,
                       STRING_AGG(DISTINCT wcg.genre, ', ') as genres
                FROM webtoon_common wc
                LEFT JOIN webtoon_common_author wca ON wc.id = wca.webtoon_id
                LEFT JOIN webtoon_common_genre wcg ON wc.id = wcg.webtoon_id
                GROUP BY wc.id, wc.title, wc.summary, wc.image_url, wc.platform, wc.publish_date, wc.created_at, wc.updated_at, wc.version
                """;
            List<Map<String, Object>> webtoons = jdbcTemplate.queryForList(sql);
            System.out.println("Webtoons count: " + webtoons.size());
            return ResponseEntity.ok(webtoons);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body(null);
        }
    }

    @GetMapping("/webtoons/paginated")
    public ResponseEntity<PaginatedResponse> getWebtoonsPaginated(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "latest") String sort,
            @RequestParam(defaultValue = "desc") String order,
            @RequestParam(required = false) String genre,
            @RequestParam(required = false) String search) {

        try {
            int offset = (Math.max(1, page) - 1) * limit;
            String whereClause = buildWhereClause(search, genre, null, "webtoon");
            String orderClause = buildOrderClause(sort, order, "webtoon");

            String countSql = "SELECT COUNT(*) FROM webtoon_common wc" + whereClause;
            String dataSql = """
            SELECT wc.*, 
                   STRING_AGG(DISTINCT wca.author, ', ') as authors,
                   STRING_AGG(DISTINCT wcg.genre, ', ') as genres
            FROM webtoon_common wc
            LEFT JOIN webtoon_common_author wca ON wc.id = wca.webtoon_id
            LEFT JOIN webtoon_common_genre wcg ON wc.id = wcg.webtoon_id
            """ + whereClause + """
            GROUP BY wc.id, wc.title, wc.summary, wc.image_url, wc.platform, wc.publish_date, wc.created_at, wc.updated_at, wc.version
            """ + orderClause + """
            LIMIT ? OFFSET ?
            """;

            Object[] params = buildParams(search, genre, "webtoon");
            Object[] dataParams = new Object[params.length + 2];
            System.arraycopy(params, 0, dataParams, 0, params.length);
            dataParams[params.length] = limit;
            dataParams[params.length + 1] = offset;

            long totalCount = jdbcTemplate.queryForObject(countSql, params, Long.class);
            List<Map<String, Object>> webtoons = jdbcTemplate.queryForList(dataSql, dataParams);
            int totalPages = (int) Math.ceil((double) totalCount / limit);

            PaginatedResponse response = new PaginatedResponse(webtoons, page, totalPages, totalCount, limit);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body(null);
        }
    }

    @GetMapping("/webtoons/genre/{genreName}")
    public ResponseEntity<List<Map<String, Object>>> getWebtoonsByGenre(@PathVariable String genreName) {
        try {
            String sql = """
                SELECT wc.*, 
                       STRING_AGG(DISTINCT wca.author, ', ') as authors,
                       STRING_AGG(DISTINCT wcg.genre, ', ') as genres
                FROM webtoon_common wc
                LEFT JOIN webtoon_common_author wca ON wc.id = wca.webtoon_id
                LEFT JOIN webtoon_common_genre wcg ON wc.id = wcg.webtoon_id
                WHERE wc.id IN (
                    SELECT webtoon_id FROM webtoon_common_genre WHERE genre = ?
                )
                GROUP BY wc.id, wc.title, wc.summary, wc.image_url, wc.platform, wc.publish_date, wc.created_at, wc.updated_at, wc.version
                """;
            List<Map<String, Object>> webtoons = jdbcTemplate.queryForList(sql, genreName);
            return ResponseEntity.ok(webtoons);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body(null);
        }
    }

    @GetMapping("/webtoons/author/{authorName}")
    public ResponseEntity<List<Map<String, Object>>> getWebtoonsByAuthor(@PathVariable String authorName) {
        try {
            String sql = """
                SELECT wc.*, 
                       STRING_AGG(DISTINCT wca.author, ', ') as authors,
                       STRING_AGG(DISTINCT wcg.genre, ', ') as genres
                FROM webtoon_common wc
                LEFT JOIN webtoon_common_author wca ON wc.id = wca.webtoon_id
                LEFT JOIN webtoon_common_genre wcg ON wc.id = wcg.webtoon_id
                WHERE wc.id IN (
                    SELECT webtoon_id FROM webtoon_common_author WHERE author = ?
                )
                GROUP BY wc.id, wc.title, wc.summary, wc.image_url, wc.platform, wc.publish_date, wc.created_at, wc.updated_at, wc.version
                """;
            List<Map<String, Object>> webtoons = jdbcTemplate.queryForList(sql, authorName);
            return ResponseEntity.ok(webtoons);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body(null);
        }
    }

    // ===== 웹소설 API =====
    @GetMapping("/novels")
    public ResponseEntity<List<Map<String, Object>>> getNovels() {
        try {
            String sql = """
                SELECT nc.*, 
                       STRING_AGG(DISTINCT nca.author, ', ') as authors,
                       STRING_AGG(DISTINCT ncg.genre, ', ') as genres
                FROM novel_common nc
                LEFT JOIN novel_common_author nca ON nc.id = nca.novel_id
                LEFT JOIN novel_common_genre ncg ON nc.id = ncg.novel_id
                GROUP BY nc.id, nc.title, nc.summary, nc.image_url, nc.age_rating, nc.publisher, nc.status, nc.created_at, nc.updated_at, nc.version
                """;
            List<Map<String, Object>> novels = jdbcTemplate.queryForList(sql);
            System.out.println("Novels count: " + novels.size());
            return ResponseEntity.ok(novels);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body(null);
        }
    }

    @GetMapping("/novels/paginated")
    public ResponseEntity<PaginatedResponse> getNovelsPaginated(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "rating") String sort,
            @RequestParam(defaultValue = "desc") String order,
            @RequestParam(required = false) String genre,
            @RequestParam(required = false) String search) {

        try {
            int offset = (Math.max(1, page) - 1) * limit;
            String whereClause = buildWhereClause(search, genre, null, "novel");
            String orderClause = buildOrderClause(sort, order, "novel");

            String countSql = "SELECT COUNT(*) FROM novel_common nc" + whereClause;
            String dataSql = """
            SELECT nc.*, 
                   STRING_AGG(DISTINCT nca.author, ', ') as authors,
                   STRING_AGG(DISTINCT ncg.genre, ', ') as genres
            FROM novel_common nc
            LEFT JOIN novel_common_author nca ON nc.id = nca.novel_id
            LEFT JOIN novel_common_genre ncg ON nc.id = ncg.novel_id
            """ + whereClause + """
            GROUP BY nc.id, nc.title, nc.summary, nc.image_url, nc.age_rating, nc.publisher, nc.status, nc.created_at, nc.updated_at, nc.version
            """ + orderClause + """
            LIMIT ? OFFSET ?
            """;

            Object[] params = buildParams(search, genre, "novel");
            Object[] dataParams = new Object[params.length + 2];
            System.arraycopy(params, 0, dataParams, 0, params.length);
            dataParams[params.length] = limit;
            dataParams[params.length + 1] = offset;

            long totalCount = jdbcTemplate.queryForObject(countSql, params, Long.class);
            List<Map<String, Object>> novels = jdbcTemplate.queryForList(dataSql, dataParams);
            int totalPages = (int) Math.ceil((double) totalCount / limit);

            PaginatedResponse response = new PaginatedResponse(novels, page, totalPages, totalCount, limit);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body(null);
        }
    }

    @GetMapping("/novels/genre/{genreName}")
    public ResponseEntity<List<Map<String, Object>>> getNovelsByGenre(@PathVariable String genreName) {
        try {
            String sql = """
                SELECT nc.*, 
                       STRING_AGG(DISTINCT nca.author, ', ') as authors,
                       STRING_AGG(DISTINCT ncg.genre, ', ') as genres
                FROM novel_common nc
                LEFT JOIN novel_common_author nca ON nc.id = nca.novel_id
                LEFT JOIN novel_common_genre ncg ON nc.id = ncg.novel_id
                WHERE nc.id IN (
                    SELECT novel_id FROM novel_common_genre WHERE genre = ?
                )
                GROUP BY nc.id, nc.title, nc.summary, nc.image_url, nc.age_rating, nc.publisher, nc.status, nc.created_at, nc.updated_at, nc.version
                """;
            List<Map<String, Object>> novels = jdbcTemplate.queryForList(sql, genreName);
            return ResponseEntity.ok(novels);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body(null);
        }
    }

    @GetMapping("/novels/author/{authorName}")
    public ResponseEntity<List<Map<String, Object>>> getNovelsByAuthor(@PathVariable String authorName) {
        try {
            String sql = """
                SELECT nc.*, 
                       STRING_AGG(DISTINCT nca.author, ', ') as authors,
                       STRING_AGG(DISTINCT ncg.genre, ', ') as genres
                FROM novel_common nc
                LEFT JOIN novel_common_author nca ON nc.id = nca.novel_id
                LEFT JOIN novel_common_genre ncg ON nc.id = ncg.novel_id
                WHERE nc.id IN (
                    SELECT novel_id FROM novel_common_author WHERE author = ?
                )
                GROUP BY nc.id, nc.title, nc.summary, nc.image_url, nc.age_rating, nc.publisher, nc.status, nc.created_at, nc.updated_at, nc.version
                """;
            List<Map<String, Object>> novels = jdbcTemplate.queryForList(sql, authorName);
            return ResponseEntity.ok(novels);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body(null);
        }
    }

    // ===== OTT 콘텐츠 API =====
    @GetMapping("/ott-content")
    public ResponseEntity<List<Map<String, Object>>> getOttContent() {
        try {
            String sql = """
                SELECT oc.*, 
                       STRING_AGG(DISTINCT oca.actor, ', ') as actors,
                       STRING_AGG(DISTINCT ocg.genre, ', ') as genres
                FROM ott_common oc
                LEFT JOIN ott_common_actors oca ON oc.id = oca.ott_id
                LEFT JOIN ott_common_genre ocg ON oc.id = ocg.ott_id
                GROUP BY oc.id, oc.title, oc.description, oc.creator, oc.image_url, oc.maturity_rating, oc.thumbnail, oc.type, oc.release_year, oc.created_at, oc.updated_at, oc.version
                """;
            List<Map<String, Object>> ottContent = jdbcTemplate.queryForList(sql);
            System.out.println("OTT content count: " + ottContent.size());
            return ResponseEntity.ok(ottContent);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body(null);
        }
    }

    @GetMapping("/ott-content/paginated")
    public ResponseEntity<PaginatedResponse> getOttContentPaginated(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "latest") String sort,
            @RequestParam(defaultValue = "desc") String order,
            @RequestParam(required = false) String genre,
            @RequestParam(required = false) String search) {

        try {
            int offset = (Math.max(1, page) - 1) * limit;
            String whereClause = buildWhereClause(search, genre, null, "ott");
            String orderClause = buildOrderClause(sort, order, "ott");

            String countSql = "SELECT COUNT(*) FROM ott_common oc" + whereClause;
            String dataSql = """
            SELECT oc.*, 
                   STRING_AGG(DISTINCT oca.actor, ', ') as actors,
                   STRING_AGG(DISTINCT ocg.genre, ', ') as genres
            FROM ott_common oc
            LEFT JOIN ott_common_actors oca ON oc.id = oca.ott_id
            LEFT JOIN ott_common_genre ocg ON oc.id = ocg.ott_id
            """ + whereClause + """
            GROUP BY oc.id, oc.title, oc.description, oc.creator, oc.image_url, oc.maturity_rating, oc.thumbnail, oc.type, oc.release_year, oc.created_at, oc.updated_at, oc.version
            """ + orderClause + """
            LIMIT ? OFFSET ?
            """;

            Object[] params = buildParams(search, genre, "ott");
            Object[] dataParams = new Object[params.length + 2];
            System.arraycopy(params, 0, dataParams, 0, params.length);
            dataParams[params.length] = limit;
            dataParams[params.length + 1] = offset;

            long totalCount = jdbcTemplate.queryForObject(countSql, params, Long.class);
            List<Map<String, Object>> ottContent = jdbcTemplate.queryForList(dataSql, dataParams);
            int totalPages = (int) Math.ceil((double) totalCount / limit);

            PaginatedResponse response = new PaginatedResponse(ottContent, page, totalPages, totalCount, limit);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body(null);
        }
    }

    @GetMapping("/ott-content/genre/{genreName}")
    public ResponseEntity<List<Map<String, Object>>> getOttContentByGenre(@PathVariable String genreName) {
        try {
            String sql = """
                SELECT oc.*, 
                       STRING_AGG(DISTINCT oca.actor, ', ') as actors,
                       STRING_AGG(DISTINCT ocg.genre, ', ') as genres
                FROM ott_common oc
                LEFT JOIN ott_common_actors oca ON oc.id = oca.ott_id
                LEFT JOIN ott_common_genre ocg ON oc.id = ocg.ott_id
                WHERE oc.id IN (
                    SELECT ott_id FROM ott_common_genre WHERE genre = ?
                )
                GROUP BY oc.id, oc.title, oc.description, oc.creator, oc.image_url, oc.maturity_rating, oc.thumbnail, oc.type, oc.release_year, oc.created_at, oc.updated_at, oc.version
                """;
            List<Map<String, Object>> ottContent = jdbcTemplate.queryForList(sql, genreName);
            return ResponseEntity.ok(ottContent);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body(null);
        }
    }

    @GetMapping("/ott-content/actor/{actorName}")
    public ResponseEntity<List<Map<String, Object>>> getOttContentByActor(@PathVariable String actorName) {
        try {
            String sql = """
                SELECT oc.*, 
                       STRING_AGG(DISTINCT oca.actor, ', ') as actors,
                       STRING_AGG(DISTINCT ocg.genre, ', ') as genres
                FROM ott_common oc
                LEFT JOIN ott_common_actors oca ON oc.id = oca.ott_id
                LEFT JOIN ott_common_genre ocg ON oc.id = ocg.ott_id
                WHERE oc.id IN (
                    SELECT ott_id FROM ott_common_actors WHERE actor = ?
                )
                GROUP BY oc.id, oc.title, oc.description, oc.creator, oc.image_url, oc.maturity_rating, oc.thumbnail, oc.type, oc.release_year, oc.created_at, oc.updated_at, oc.version
                """;
            List<Map<String, Object>> ottContent = jdbcTemplate.queryForList(sql, actorName);
            return ResponseEntity.ok(ottContent);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body(null);
        }
    }

    // ===== 통합 검색 API =====
    @GetMapping("/search")
    public ResponseEntity<List<Map<String, Object>>> searchContent(@RequestParam String keyword, @RequestParam String type) {
        try {
            String sql = "";
            switch (type.toLowerCase()) {
                case "webtoon":
                    sql = """
                        SELECT wc.*, 'webtoon' as content_type,
                               STRING_AGG(DISTINCT wca.author, ', ') as authors,
                               STRING_AGG(DISTINCT wcg.genre, ', ') as genres
                        FROM webtoon_common wc
                        LEFT JOIN webtoon_common_author wca ON wc.id = wca.webtoon_id
                        LEFT JOIN webtoon_common_genre wcg ON wc.id = wcg.webtoon_id
                        WHERE wc.title LIKE ? OR wc.summary LIKE ?
                        GROUP BY wc.id, wc.title, wc.summary, wc.image_url, wc.platform, wc.publish_date, wc.created_at, wc.updated_at, wc.version
                        """;
                    break;
                case "novel":
                    sql = """
                        SELECT nc.*, 'novel' as content_type,
                               STRING_AGG(DISTINCT nca.author, ', ') as authors,
                               STRING_AGG(DISTINCT ncg.genre, ', ') as genres
                        FROM novel_common nc
                        LEFT JOIN novel_common_author nca ON nc.id = nca.novel_id
                        LEFT JOIN novel_common_genre ncg ON nc.id = ncg.novel_id
                        WHERE nc.title LIKE ? OR nc.summary LIKE ?
                        GROUP BY nc.id, nc.title, nc.summary, nc.image_url, nc.age_rating, nc.publisher, nc.status, nc.created_at, nc.updated_at, nc.version
                        """;
                    break;
                case "movie":
                    sql = """
                        SELECT mc.*, 'movie' as content_type,
                               STRING_AGG(DISTINCT mca.actor, ', ') as actors,
                               STRING_AGG(DISTINCT mcg.genre, ', ') as genres
                        FROM movie_common mc
                        LEFT JOIN movie_common_actors mca ON mc.id = mca.movie_id
                        LEFT JOIN movie_common_genre mcg ON mc.id = mcg.movie_id
                        WHERE mc.title LIKE ? OR mc.summary LIKE ?
                        GROUP BY mc.id, mc.title, mc.summary, mc.director, mc.image_url, mc.release_date, mc.country, mc.age_rating, mc.rating, mc.running_time, mc.total_audience, mc.is_rerelease, mc.reservation_rate, mc.created_at, mc.updated_at, mc.version
                        """;
                    break;
                case "ott":
                    sql = """
                        SELECT oc.*, 'ott' as content_type,
                               STRING_AGG(DISTINCT oca.actor, ', ') as actors,
                               STRING_AGG(DISTINCT ocg.genre, ', ') as genres
                        FROM ott_common oc
                        LEFT JOIN ott_common_actors oca ON oc.id = oca.ott_id
                        LEFT JOIN ott_common_genre ocg ON oc.id = ocg.ott_id
                        WHERE oc.title LIKE ? OR oc.description LIKE ?
                        GROUP BY oc.id, oc.title, oc.description, oc.creator, oc.image_url, oc.maturity_rating, oc.thumbnail, oc.type, oc.release_year, oc.created_at, oc.updated_at, oc.version
                        """;
                    break;
                case "game":
                    sql = """
                        SELECT gc.*, 'game' as content_type,
                               STRING_AGG(DISTINCT gcp.publisher, ', ') as publishers,
                               STRING_AGG(DISTINCT gcd.developer, ', ') as developers
                        FROM game_common gc
                        LEFT JOIN game_common_publisher gcp ON gc.id = gcp.game_id
                        LEFT JOIN game_common_developer gcd ON gc.id = gcd.game_id
                        WHERE gc.title LIKE ? OR gc.summary LIKE ?
                        GROUP BY gc.id, gc.title, gc.summary, gc.image_url, gc.platform, gc.required_age, gc.final_price, gc.initial_price, gc.created_at, gc.updated_at, gc.version
                        """;
                    break;
                default:
                    return ResponseEntity.badRequest().body(null);
            }

            String searchPattern = "%" + keyword + "%";
            List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, searchPattern, searchPattern);
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body(null);
        }
    }
}