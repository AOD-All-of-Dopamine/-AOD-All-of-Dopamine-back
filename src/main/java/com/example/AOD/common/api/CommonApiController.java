package com.example.AOD.common.api;

import com.example.AOD.common.commonDomain.*;
import com.example.AOD.common.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/common")
@CrossOrigin(origins = "*") // 개발 단계에서는 모든 오리진 허용, 운영 환경에서는 명확한 오리진 지정 필요
public class CommonApiController {

    private final NovelCommonRepository novelCommonRepository;
    private final MovieCommonRepository movieCommonRepository;
    private final OTTCommonRepository ottCommonRepository;
    private final WebtoonCommonRepository webtoonCommonRepository;
    private final GameCommonRepository gameCommonRepository;

    @Autowired
    public CommonApiController(
            NovelCommonRepository novelCommonRepository,
            MovieCommonRepository movieCommonRepository,
            OTTCommonRepository ottCommonRepository,
            WebtoonCommonRepository webtoonCommonRepository,
            GameCommonRepository gameCommonRepository) {
        this.novelCommonRepository = novelCommonRepository;
        this.movieCommonRepository = movieCommonRepository;
        this.ottCommonRepository = ottCommonRepository;
        this.webtoonCommonRepository = webtoonCommonRepository;
        this.gameCommonRepository = gameCommonRepository;
    }

    // ===== 소설 API =====
    @GetMapping("/novels")
    public ResponseEntity<List<NovelCommon>> getAllNovels() {
        return ResponseEntity.ok(novelCommonRepository.findAll());
    }

    @GetMapping("/novels/{id}")
    public ResponseEntity<NovelCommon> getNovel(@PathVariable Long id) {
        return novelCommonRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/novels/search")
    public ResponseEntity<List<NovelCommon>> searchNovels(@RequestParam String keyword) {
        return ResponseEntity.ok(novelCommonRepository.findByTitleContainingIgnoreCase(keyword));
    }

    @GetMapping("/novels/page")
    public ResponseEntity<Map<String, Object>> getPaginatedNovels(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String direction) {

        Sort.Direction sortDirection = "desc".equalsIgnoreCase(direction) ?
                Sort.Direction.DESC : Sort.Direction.ASC;

        Pageable pageable = PageRequest.of(page, size, Sort.by(sortDirection, sortBy));
        Page<NovelCommon> novelPage = novelCommonRepository.findAll(pageable);

        Map<String, Object> response = new HashMap<>();
        response.put("content", novelPage.getContent());
        response.put("currentPage", novelPage.getNumber());
        response.put("totalItems", novelPage.getTotalElements());
        response.put("totalPages", novelPage.getTotalPages());

        return ResponseEntity.ok(response);
    }

    // ===== 영화 API =====
    @GetMapping("/movies")
    public ResponseEntity<List<MovieCommon>> getAllMovies() {
        return ResponseEntity.ok(movieCommonRepository.findAll());
    }

    @GetMapping("/movies/{id}")
    public ResponseEntity<MovieCommon> getMovie(@PathVariable Long id) {
        return movieCommonRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/movies/search")
    public ResponseEntity<List<MovieCommon>> searchMovies(@RequestParam String keyword) {
        return ResponseEntity.ok(movieCommonRepository.findByTitleContainingIgnoreCase(keyword));
    }

    @GetMapping("/movies/page")
    public ResponseEntity<Map<String, Object>> getPaginatedMovies(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String direction) {

        Sort.Direction sortDirection = "desc".equalsIgnoreCase(direction) ?
                Sort.Direction.DESC : Sort.Direction.ASC;

        Pageable pageable = PageRequest.of(page, size, Sort.by(sortDirection, sortBy));
        Page<MovieCommon> moviePage = movieCommonRepository.findAll(pageable);

        Map<String, Object> response = new HashMap<>();
        response.put("content", moviePage.getContent());
        response.put("currentPage", moviePage.getNumber());
        response.put("totalItems", moviePage.getTotalElements());
        response.put("totalPages", moviePage.getTotalPages());

        return ResponseEntity.ok(response);
    }

    // ===== OTT API =====
    @GetMapping("/otts")
    public ResponseEntity<List<OTTCommon>> getAllOTTs() {
        return ResponseEntity.ok(ottCommonRepository.findAll());
    }

    @GetMapping("/otts/{id}")
    public ResponseEntity<OTTCommon> getOTT(@PathVariable Long id) {
        return ottCommonRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/otts/search")
    public ResponseEntity<List<OTTCommon>> searchOTTs(@RequestParam String keyword) {
        return ResponseEntity.ok(ottCommonRepository.findByTitleContainingIgnoreCase(keyword));
    }

    @GetMapping("/otts/page")
    public ResponseEntity<Map<String, Object>> getPaginatedOTTs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String direction) {

        Sort.Direction sortDirection = "desc".equalsIgnoreCase(direction) ?
                Sort.Direction.DESC : Sort.Direction.ASC;

        Pageable pageable = PageRequest.of(page, size, Sort.by(sortDirection, sortBy));
        Page<OTTCommon> ottPage = ottCommonRepository.findAll(pageable);

        Map<String, Object> response = new HashMap<>();
        response.put("content", ottPage.getContent());
        response.put("currentPage", ottPage.getNumber());
        response.put("totalItems", ottPage.getTotalElements());
        response.put("totalPages", ottPage.getTotalPages());

        return ResponseEntity.ok(response);
    }

    // ===== 웹툰 API =====
    @GetMapping("/webtoons")
    public ResponseEntity<List<WebtoonCommon>> getAllWebtoons() {
        return ResponseEntity.ok(webtoonCommonRepository.findAll());
    }

    @GetMapping("/webtoons/{id}")
    public ResponseEntity<WebtoonCommon> getWebtoon(@PathVariable Long id) {
        return webtoonCommonRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/webtoons/search")
    public ResponseEntity<List<WebtoonCommon>> searchWebtoons(@RequestParam String keyword) {
        return ResponseEntity.ok(webtoonCommonRepository.findByTitleContainingIgnoreCase(keyword));
    }

    @GetMapping("/webtoons/page")
    public ResponseEntity<Map<String, Object>> getPaginatedWebtoons(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String direction) {

        Sort.Direction sortDirection = "desc".equalsIgnoreCase(direction) ?
                Sort.Direction.DESC : Sort.Direction.ASC;

        Pageable pageable = PageRequest.of(page, size, Sort.by(sortDirection, sortBy));
        Page<WebtoonCommon> webtoonPage = webtoonCommonRepository.findAll(pageable);

        Map<String, Object> response = new HashMap<>();
        response.put("content", webtoonPage.getContent());
        response.put("currentPage", webtoonPage.getNumber());
        response.put("totalItems", webtoonPage.getTotalElements());
        response.put("totalPages", webtoonPage.getTotalPages());

        return ResponseEntity.ok(response);
    }

    // ===== 게임 API =====
    @GetMapping("/games")
    public ResponseEntity<List<GameCommon>> getAllGames() {
        return ResponseEntity.ok(gameCommonRepository.findAll());
    }

    @GetMapping("/games/{id}")
    public ResponseEntity<GameCommon> getGame(@PathVariable Long id) {
        return gameCommonRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/games/search")
    public ResponseEntity<List<GameCommon>> searchGames(@RequestParam String keyword) {
        return ResponseEntity.ok(gameCommonRepository.findByTitleContainingIgnoreCase(keyword));
    }

    @GetMapping("/games/page")
    public ResponseEntity<Map<String, Object>> getPaginatedGames(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String direction) {

        Sort.Direction sortDirection = "desc".equalsIgnoreCase(direction) ?
                Sort.Direction.DESC : Sort.Direction.ASC;

        Pageable pageable = PageRequest.of(page, size, Sort.by(sortDirection, sortBy));
        Page<GameCommon> gamePage = gameCommonRepository.findAll(pageable);

        Map<String, Object> response = new HashMap<>();
        response.put("content", gamePage.getContent());
        response.put("currentPage", gamePage.getNumber());
        response.put("totalItems", gamePage.getTotalElements());
        response.put("totalPages", gamePage.getTotalPages());

        return ResponseEntity.ok(response);
    }

    // ===== 통합 API =====
    @GetMapping("/counts")
    public ResponseEntity<Map<String, Object>> getContentCounts() {
        Map<String, Object> counts = new HashMap<>();

        long novelCount = novelCommonRepository.count();
        long movieCount = movieCommonRepository.count();
        long ottCount = ottCommonRepository.count();
        long webtoonCount = webtoonCommonRepository.count();
        long gameCount = gameCommonRepository.count();
        long totalCount = novelCount + movieCount + ottCount + webtoonCount + gameCount;

        counts.put("novels", novelCount);
        counts.put("movies", movieCount);
        counts.put("otts", ottCount);
        counts.put("webtoons", webtoonCount);
        counts.put("games", gameCount);
        counts.put("total", totalCount);

        return ResponseEntity.ok(counts);
    }

    // 통합 검색 API (모든 콘텐츠 유형에서 검색)
    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> searchAllContent(@RequestParam String keyword) {
        Map<String, Object> results = new HashMap<>();

        results.put("novels", novelCommonRepository.findByTitleContainingIgnoreCase(keyword));
        results.put("movies", movieCommonRepository.findByTitleContainingIgnoreCase(keyword));
        results.put("otts", ottCommonRepository.findByTitleContainingIgnoreCase(keyword));
        results.put("webtoons", webtoonCommonRepository.findByTitleContainingIgnoreCase(keyword));
        results.put("games", gameCommonRepository.findByTitleContainingIgnoreCase(keyword));

        return ResponseEntity.ok(results);
    }

    // 모든 콘텐츠 유형의 데이터를 반환하는 통합 API
    @GetMapping("/all")
    public ResponseEntity<Map<String, Object>> getAllContent() {
        Map<String, Object> allContent = new HashMap<>();

        allContent.put("novels", novelCommonRepository.findAll());
        allContent.put("movies", movieCommonRepository.findAll());
        allContent.put("otts", ottCommonRepository.findAll());
        allContent.put("webtoons", webtoonCommonRepository.findAll());
        allContent.put("games", gameCommonRepository.findAll());

        return ResponseEntity.ok(allContent);
    }
}