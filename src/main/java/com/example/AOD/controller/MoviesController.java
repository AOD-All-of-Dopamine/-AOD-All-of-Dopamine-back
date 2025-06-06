package com.example.AOD.controller;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "http://localhost:3000")
public class MoviesController {
    @Autowired
    private JdbcTemplate jdbcTemplate;

    // ===== 웹툰 관련 API =====
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

    // ===== OTT 콘텐츠 관련 API =====
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

    // ===== 소설 관련 API =====
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

    // ===== 영화 관련 API =====
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

    // ===== 게임 관련 API =====
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