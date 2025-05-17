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

    @GetMapping("/movies")
    public ResponseEntity<List<Map<String, Object>>> getMovies() {
        try {
            // 직접 SQL 쿼리를 사용하여 movies 테이블에서 데이터를 가져옴
            String sql = "SELECT * FROM movies";
            List<Map<String, Object>> movies = jdbcTemplate.queryForList(sql);
            System.out.println("Movies count: " + movies.size());
            System.out.println("First movie: " + (movies.isEmpty() ? "None" : movies.get(0)));
            return ResponseEntity.ok(movies);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body(null);
        }
    }

    @GetMapping("/steam-games")
    public ResponseEntity<List<Map<String, Object>>> getSteamGames() {
        try {
            // 직접 SQL 쿼리를 사용하여 steam_game 테이블에서 데이터를 가져옴
            String sql = "SELECT * FROM steam_game";
            List<Map<String, Object>> games = jdbcTemplate.queryForList(sql);
            System.out.println("Games count: " + games.size());
            System.out.println("First game: " + (games.isEmpty() ? "None" : games.get(0)));
            return ResponseEntity.ok(games);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body(null);
        }
    }

    @GetMapping("/webtoons")
    public ResponseEntity<List<Map<String, Object>>> getWebtoons() {
        try {
            // 직접 SQL 쿼리를 사용하여 webtoon 테이블에서 데이터를 가져옴
            String sql = "SELECT * FROM webtoon";
            List<Map<String, Object>> webtoons = jdbcTemplate.queryForList(sql);
            System.out.println("Webtoons count: " + webtoons.size());
            System.out.println("First webtoon: " + (webtoons.isEmpty() ? "None" : webtoons.get(0)));
            return ResponseEntity.ok(webtoons);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body(null);
        }
    }

    @GetMapping("/webtoons/genre/{genreId}")
    public ResponseEntity<List<Map<String, Object>>> getWebtoonsByGenre(@PathVariable int genreId) {
        try {
            // 장르 ID를 사용하여 특정 장르의 웹툰 데이터 가져오기
            String sql = "SELECT w.* FROM webtoon w JOIN webtoon_genre_mapping wgm ON w.id = wgm.webtoon_id WHERE wgm.genre_id = ?";
            List<Map<String, Object>> webtoons = jdbcTemplate.queryForList(sql, genreId);
            return ResponseEntity.ok(webtoons);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body(null);
        }
    }

    @GetMapping("/novels")
    public ResponseEntity<List<Map<String, Object>>> getNovels() {
        try {
            // naver_series_novel 테이블에서 데이터를 가져오도록 수정
            String sql = "SELECT * FROM naver_series_novel";
            List<Map<String, Object>> novels = jdbcTemplate.queryForList(sql);
            System.out.println("Novels count: " + novels.size());
            System.out.println("First novel: " + (novels.isEmpty() ? "None" : novels.get(0)));

            // 만약 naver_series_novel 테이블에서 데이터가 없다면 novel_common 테이블도 확인
            if (novels.isEmpty()) {
                System.out.println("No novels found in naver_series_novel, trying novel_common table");
                sql = "SELECT * FROM novel_common";
                novels = jdbcTemplate.queryForList(sql);
                System.out.println("Novels count from novel_common: " + novels.size());
                System.out.println("First novel from novel_common: " + (novels.isEmpty() ? "None" : novels.get(0)));
            }

            return ResponseEntity.ok(novels);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body(null);
        }
    }

    @GetMapping("/novels/genre/{genreId}")
    public ResponseEntity<List<Map<String, Object>>> getNovelsByGenre(@PathVariable int genreId) {
        try {
            // naver_series_novel에서 장르별 데이터 조회 먼저 시도
            String sql = "SELECT n.* FROM naver_series_novel n JOIN naver_series_novel_genre ngm ON n.id = ngm.novel_id WHERE ngm.genre_id = ?";
            List<Map<String, Object>> novels = jdbcTemplate.queryForList(sql, genreId);

            // 결과가 없으면 novel_common에서 조회
            if (novels.isEmpty()) {
                sql = "SELECT n.* FROM novel_common n JOIN novel_genre_mapping ngm ON n.id = ngm.novel_id WHERE ngm.genre_id = ?";
                novels = jdbcTemplate.queryForList(sql, genreId);
            }

            return ResponseEntity.ok(novels);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body(null);
        }
    }

    @GetMapping("/netflix-content")
    public ResponseEntity<List<Map<String, Object>>> getNetflixContent() {
        try {
            // 직접 SQL 쿼리를 사용하여 netflix_content 테이블에서 데이터를 가져옴
            String sql = "SELECT * FROM netflix_content";
            List<Map<String, Object>> netflixContent = jdbcTemplate.queryForList(sql);
            System.out.println("Netflix content count: " + netflixContent.size());
            System.out.println("First content: " + (netflixContent.isEmpty() ? "None" : netflixContent.get(0)));
            return ResponseEntity.ok(netflixContent);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body(null);
        }
    }

    @GetMapping("/netflix-content/genre/{genreId}")
    public ResponseEntity<List<Map<String, Object>>> getNetflixContentByGenre(@PathVariable int genreId) {
        try {
            // 장르 ID를 사용하여 특정 장르의 넷플릭스 컨텐츠 데이터 가져오기
            String sql = "SELECT nc.* FROM netflix_content nc JOIN netflix_content_genre ncg ON nc.content_id = ncg.content_id WHERE ncg.genre_id = ?";
            List<Map<String, Object>> netflixContent = jdbcTemplate.queryForList(sql, genreId);
            return ResponseEntity.ok(netflixContent);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body(null);
        }
    }

    @GetMapping("/netflix-content/type/{type}")
    public ResponseEntity<List<Map<String, Object>>> getNetflixContentByType(@PathVariable String type) {
        try {
            // 컨텐츠 타입(영화, 시리즈 등)으로 넷플릭스 컨텐츠 필터링
            String sql = "SELECT * FROM netflix_content WHERE type = ?";
            List<Map<String, Object>> netflixContent = jdbcTemplate.queryForList(sql, type);
            return ResponseEntity.ok(netflixContent);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body(null);
        }
    }

    @GetMapping("/netflix-content/year/{year}")
    public ResponseEntity<List<Map<String, Object>>> getNetflixContentByYear(@PathVariable String year) {
        try {
            // 출시 연도로 넷플릭스 컨텐츠 필터링
            String sql = "SELECT * FROM netflix_content WHERE release_year = ?";
            List<Map<String, Object>> netflixContent = jdbcTemplate.queryForList(sql, year);
            return ResponseEntity.ok(netflixContent);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body(null);
        }
    }

    @GetMapping("/netflix-content/search")
    public ResponseEntity<List<Map<String, Object>>> searchNetflixContent(@RequestParam String keyword) {
        try {
            // 제목이나 설명에서 키워드 검색
            String sql = "SELECT * FROM netflix_content WHERE title LIKE ? OR description LIKE ?";
            String searchPattern = "%" + keyword + "%";
            List<Map<String, Object>> netflixContent = jdbcTemplate.queryForList(sql, searchPattern, searchPattern);
            return ResponseEntity.ok(netflixContent);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body(null);
        }
    }
}