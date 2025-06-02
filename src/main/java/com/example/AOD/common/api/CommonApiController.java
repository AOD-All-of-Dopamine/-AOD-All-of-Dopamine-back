package com.example.AOD.common.api;

import com.example.AOD.common.commonDomain.*;
import com.example.AOD.common.repository.*;
import com.example.AOD.common.service.ContentIntegrationService;
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
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/common")
@CrossOrigin(origins = "*")
public class CommonApiController {

    private final NovelCommonRepository novelCommonRepository;
    private final MovieCommonRepository movieCommonRepository;
    private final OTTCommonRepository ottCommonRepository;
    private final WebtoonCommonRepository webtoonCommonRepository;
    private final GameCommonRepository gameCommonRepository;
    private final ContentIntegrationService contentIntegrationService;

    @Autowired
    public CommonApiController(
            NovelCommonRepository novelCommonRepository,
            MovieCommonRepository movieCommonRepository,
            OTTCommonRepository ottCommonRepository,
            WebtoonCommonRepository webtoonCommonRepository,
            GameCommonRepository gameCommonRepository,
            ContentIntegrationService contentIntegrationService) {
        this.novelCommonRepository = novelCommonRepository;
        this.movieCommonRepository = movieCommonRepository;
        this.ottCommonRepository = ottCommonRepository;
        this.webtoonCommonRepository = webtoonCommonRepository;
        this.gameCommonRepository = gameCommonRepository;
        this.contentIntegrationService = contentIntegrationService;
    }

    // ===== 소설 API =====
    @GetMapping("/novels")
    public ResponseEntity<List<Map<String, Object>>> getAllNovels() {
        List<NovelCommon> novels = novelCommonRepository.findAll();
        List<Map<String, Object>> result = novels.stream()
                .map(this::novelToMap)
                .collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/novels/{id}")
    public ResponseEntity<Map<String, Object>> getNovel(@PathVariable Long id) {
        return novelCommonRepository.findById(id)
                .map(this::novelToMap)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/novels/search")
    public ResponseEntity<List<Map<String, Object>>> searchNovels(@RequestParam String keyword) {
        List<NovelCommon> novels = novelCommonRepository.findByTitleContainingIgnoreCase(keyword);
        List<Map<String, Object>> result = novels.stream()
                .map(this::novelToMap)
                .collect(Collectors.toList());
        return ResponseEntity.ok(result);
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
        response.put("content", novelPage.getContent().stream()
                .map(this::novelToMap)
                .collect(Collectors.toList()));
        response.put("currentPage", novelPage.getNumber());
        response.put("totalItems", novelPage.getTotalElements());
        response.put("totalPages", novelPage.getTotalPages());

        return ResponseEntity.ok(response);
    }

    // 플랫폼별 소설 조회
    @GetMapping("/novels/platform/{platform}")
    public ResponseEntity<List<Map<String, Object>>> getNovelsByPlatform(@PathVariable String platform) {
        List<NovelCommon> novels;

        switch (platform.toLowerCase()) {
            case "naver":
            case "naverseries":
                novels = novelCommonRepository.findAll().stream()
                        .filter(novel -> novel.isOnNaverSeries())
                        .collect(Collectors.toList());
                break;
            case "kakao":
            case "kakaopage":
                novels = novelCommonRepository.findAll().stream()
                        .filter(novel -> novel.isOnKakaoPage())
                        .collect(Collectors.toList());
                break;
            case "ridibooks":
                novels = novelCommonRepository.findAll().stream()
                        .filter(novel -> novel.isOnRidibooks())
                        .collect(Collectors.toList());
                break;
            default:
                return ResponseEntity.badRequest().build();
        }

        List<Map<String, Object>> result = novels.stream()
                .map(this::novelToMap)
                .collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    // ===== 영화 API =====
    @GetMapping("/movies")
    public ResponseEntity<List<Map<String, Object>>> getAllMovies() {
        List<MovieCommon> movies = movieCommonRepository.findAll();
        List<Map<String, Object>> result = movies.stream()
                .map(this::movieToMap)
                .collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/movies/{id}")
    public ResponseEntity<Map<String, Object>> getMovie(@PathVariable Long id) {
        return movieCommonRepository.findById(id)
                .map(this::movieToMap)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/movies/search")
    public ResponseEntity<List<Map<String, Object>>> searchMovies(@RequestParam String keyword) {
        List<MovieCommon> movies = movieCommonRepository.findByTitleContainingIgnoreCase(keyword);
        List<Map<String, Object>> result = movies.stream()
                .map(this::movieToMap)
                .collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    // 플랫폼별 영화 조회
    @GetMapping("/movies/platform/{platform}")
    public ResponseEntity<List<Map<String, Object>>> getMoviesByPlatform(@PathVariable String platform) {
        List<MovieCommon> movies;

        switch (platform.toLowerCase()) {
            case "cgv":
                movies = movieCommonRepository.findAll().stream()
                        .filter(movie -> movie.isOnCgv())
                        .collect(Collectors.toList());
                break;
            case "megabox":
                movies = movieCommonRepository.findAll().stream()
                        .filter(movie -> movie.isOnMegabox())
                        .collect(Collectors.toList());
                break;
            case "lottecinema":
                movies = movieCommonRepository.findAll().stream()
                        .filter(movie -> movie.isOnLotteCinema())
                        .collect(Collectors.toList());
                break;
            default:
                return ResponseEntity.badRequest().build();
        }

        List<Map<String, Object>> result = movies.stream()
                .map(this::movieToMap)
                .collect(Collectors.toList());
        return ResponseEntity.ok(result);
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

    // 통합 상태 조회 API
    @GetMapping("/integration-status/{contentType}")
    public ResponseEntity<Map<String, Object>> getIntegrationStatus(@PathVariable String contentType) {
        Map<String, Object> status = contentIntegrationService.getIntegrationStatus(contentType);
        return ResponseEntity.ok(status);
    }

    // 플랫폼 정보 포함 검색 API
    @GetMapping("/search/with-platforms")
    public ResponseEntity<Map<String, Object>> searchAllContentWithPlatforms(@RequestParam String keyword) {
        Map<String, Object> results = new HashMap<>();

        results.put("novels", novelCommonRepository.findByTitleContainingIgnoreCase(keyword).stream()
                .map(this::novelToMap)
                .collect(Collectors.toList()));
        results.put("movies", movieCommonRepository.findByTitleContainingIgnoreCase(keyword).stream()
                .map(this::movieToMap)
                .collect(Collectors.toList()));
        results.put("otts", ottCommonRepository.findByTitleContainingIgnoreCase(keyword).stream()
                .map(this::ottToMap)
                .collect(Collectors.toList()));
        results.put("webtoons", webtoonCommonRepository.findByTitleContainingIgnoreCase(keyword).stream()
                .map(this::webtoonToMap)
                .collect(Collectors.toList()));
        results.put("games", gameCommonRepository.findByTitleContainingIgnoreCase(keyword).stream()
                .map(this::gameToMap)
                .collect(Collectors.toList()));

        return ResponseEntity.ok(results);
    }

    // ===== 변환 메서드들 =====
    private Map<String, Object> novelToMap(NovelCommon novel) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", novel.getId());
        map.put("title", novel.getTitle());
        map.put("imageUrl", novel.getImageUrl());
        map.put("genre", novel.getGenre());
        map.put("status", novel.getStatus());
        map.put("authors", novel.getAuthors());
        map.put("ageRating", novel.getAgeRating());
        map.put("publisher", novel.getPublisher());
        map.put("createdAt", novel.getCreatedAt());
        map.put("updatedAt", novel.getUpdatedAt());

        // 플랫폼 정보 추가
        Map<String, Object> platforms = new HashMap<>();
        if (novel.getPlatformMapping() != null) {
            NovelPlatformMapping mapping = novel.getPlatformMapping();
            platforms.put("naverSeries", mapping.hasNaverSeries() ? mapping.getNaverSeriesId() : null);
            platforms.put("kakaoPage", mapping.hasKakaoPage() ? mapping.getKakaoPageId() : null);
            platforms.put("ridibooks", mapping.hasRidibooks() ? mapping.getRidibooksId() : null);
        }
        map.put("platforms", platforms);

        return map;
    }

    private Map<String, Object> movieToMap(MovieCommon movie) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", movie.getId());
        map.put("title", movie.getTitle());
        map.put("imageUrl", movie.getImageUrl());
        map.put("genre", movie.getGenre());
        map.put("releaseDate", movie.getReleaseDate());
        map.put("runningTime", movie.getRunningTime());
        map.put("director", movie.getDirector());
        map.put("actors", movie.getActors());
        map.put("ageRating", movie.getAgeRating());
        map.put("totalAudience", movie.getTotalAudience());
        map.put("summary", movie.getSummary());
        map.put("rating", movie.getRating());
        map.put("reservationRate", movie.getReservationRate());
        map.put("country", movie.getCountry());
        map.put("isRerelease", movie.getIsRerelease());
        map.put("createdAt", movie.getCreatedAt());
        map.put("updatedAt", movie.getUpdatedAt());

        // 플랫폼 정보 추가
        Map<String, Object> platforms = new HashMap<>();
        if (movie.getPlatformMapping() != null) {
            MoviePlatformMapping mapping = movie.getPlatformMapping();
            platforms.put("cgv", mapping.hasCgv() ? mapping.getCgvId() : null);
            platforms.put("megabox", mapping.hasMegabox() ? mapping.getMegaboxId() : null);
            platforms.put("lotteCinema", mapping.hasLotteCinema() ? mapping.getLotteCinemaId() : null);
        }
        map.put("platforms", platforms);

        return map;
    }

    private Map<String, Object> ottToMap(OTTCommon ott) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", ott.getId());
        map.put("title", ott.getTitle());
        map.put("imageUrl", ott.getImageUrl());
        map.put("genre", ott.getGenre());
        map.put("type", ott.getType());
        map.put("thumbnail", ott.getThumbnail());
        map.put("description", ott.getDescription());
        map.put("creator", ott.getCreator());
        map.put("maturityRating", ott.getMaturityRating());
        map.put("releaseYear", ott.getReleaseYear());
        map.put("actors", ott.getActors());
        map.put("features", ott.getFeatures());
        map.put("createdAt", ott.getCreatedAt());
        map.put("updatedAt", ott.getUpdatedAt());

        // 플랫폼 정보 추가
        Map<String, Object> platforms = new HashMap<>();
        if (ott.getPlatformMapping() != null) {
            OTTPlatformMapping mapping = ott.getPlatformMapping();
            platforms.put("netflix", mapping.hasNetflix() ? mapping.getNetflixId() : null);
            platforms.put("disneyPlus", mapping.hasDisneyPlus() ? mapping.getDisneyPlusId() : null);
            platforms.put("watcha", mapping.hasWatcha() ? mapping.getWatchaId() : null);
            platforms.put("wavve", mapping.hasWavve() ? mapping.getWavveId() : null);
        }
        map.put("platforms", platforms);

        return map;
    }

    private Map<String, Object> webtoonToMap(WebtoonCommon webtoon) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", webtoon.getId());
        map.put("title", webtoon.getTitle());
        map.put("imageUrl", webtoon.getImageUrl());
        // genre와 author는 엔티티이므로 변환 필요
        map.put("genre", webtoon.getGenre() != null ?
                webtoon.getGenre().stream().map(g -> g.getGenre()).collect(Collectors.toList()) : null);
        map.put("publishDate", webtoon.getPublishDate());
        map.put("uploadDay", webtoon.getUploadDay());
        map.put("author", webtoon.getAuthor() != null ?
                webtoon.getAuthor().stream().map(a -> a.getName()).collect(Collectors.toList()) : null);
        map.put("summary", webtoon.getSummary());
        map.put("platform", webtoon.getPlatform());
        map.put("createdAt", webtoon.getCreatedAt());
        map.put("updatedAt", webtoon.getUpdatedAt());

        // 플랫폼 정보 추가
        Map<String, Object> platforms = new HashMap<>();
        if (webtoon.getPlatformMapping() != null) {
            WebtoonPlatformMapping mapping = webtoon.getPlatformMapping();
            platforms.put("naver", mapping.hasNaver() ? mapping.getNaverId() : null);
            platforms.put("kakao", mapping.hasKakao() ? mapping.getKakaoId() : null);
        }
        map.put("platforms", platforms);

        return map;
    }

    private Map<String, Object> gameToMap(GameCommon game) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", game.getId());
        map.put("title", game.getTitle());
        map.put("imageUrl", game.getImageUrl());
        map.put("genre", game.getGenre());
        map.put("requiredAge", game.getRequiredAge());
        map.put("summary", game.getSummary());
        map.put("initialPrice", game.getInitialPrice());
        map.put("finalPrice", game.getFinalPrice());
        map.put("platform", game.getPlatform());
        map.put("publisher", game.getPublisher());
        map.put("developers", game.getDevelopers());
        map.put("createdAt", game.getCreatedAt());
        map.put("updatedAt", game.getUpdatedAt());

        // 플랫폼 정보 추가
        Map<String, Object> platforms = new HashMap<>();
        if (game.getPlatformMapping() != null) {
            GamePlatformMapping mapping = game.getPlatformMapping();
            platforms.put("steam", mapping.hasSteam() ? mapping.getSteamId() : null);
            platforms.put("epic", mapping.hasEpic() ? mapping.getEpicId() : null);
            platforms.put("gog", mapping.hasGog() ? mapping.getGogId() : null);
        }
        map.put("platforms", platforms);

        return map;
    }
}