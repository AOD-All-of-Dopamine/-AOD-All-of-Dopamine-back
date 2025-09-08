//package com.example.AOD.recommendation.service;
//
//import com.example.AOD.recommendation.domain.UserPreference;
//import com.example.AOD.recommendation.domain.ContentRating;
//import com.example.AOD.recommendation.repository.UserPreferenceRepository;
//import com.example.AOD.recommendation.repository.ContentRatingRepository;
//import com.example.AOD.recommendation.dto.*;
//import com.example.AOD.user.dto.SignUpRequest;
//
//// 플랫폼별 리포지토리 import
//import com.example.AOD.movie.CGV.repository.MovieRepository;
//import com.example.AOD.Novel.NaverSeriesNovel.repository.NaverSeriesNovelRepository;
//import com.example.AOD.Webtoon.NaverWebtoon.repository.WebtoonRepository;
//import com.example.AOD.AV.Netflix.repository.NetflixContentRepository;
//import com.example.AOD.game.StreamAPI.repository.GameRepository;
//
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.stereotype.Service;
//import org.springframework.transaction.annotation.Transactional;
//
//import java.util.*;
//import java.util.stream.Collectors;
//
//@Service
//public class UserPreferenceService {
//
//    @Autowired
//    private UserPreferenceRepository userPreferenceRepository;
//
//    @Autowired
//    private ContentRatingRepository contentRatingRepository;
//
//    // 플랫폼별 리포지토리들
//    @Autowired
//    private MovieRepository movieRepository;
//
//    @Autowired
//    private NaverSeriesNovelRepository novelRepository;
//
//    @Autowired
//    private WebtoonRepository webtoonRepository;
//
//    @Autowired
//    private NetflixContentRepository netflixRepository;
//
//    @Autowired
//    private GameRepository gameRepository;
//
//    // 초기 추천 시스템 (DTO 변환)
//    public Map<String, List<?>> getInitialRecommendations(String username) {
//        System.out.println("=== getInitialRecommendations DEBUG START ===");
//        System.out.println("Username: " + username);
//
//        Map<String, List<?>> recommendations = new HashMap<>();
//
//        Optional<UserPreference> preferenceOpt = userPreferenceRepository.findByUsername(username);
//        System.out.println("User preference found: " + preferenceOpt.isPresent());
//
//        if (preferenceOpt.isEmpty()) {
//            System.out.println("No preferences found, returning welcome recommendations");
//            return getWelcomeRecommendations();
//        }
//
//        UserPreference preference = preferenceOpt.get();
//        System.out.println("Preferred content types: " + preference.getPreferredContentTypes());
//        System.out.println("Preferred genres: " + preference.getPreferredGenres());
//
//        // 선호 콘텐츠 타입에 따른 초기 추천
//        if (preference.getPreferredContentTypes() != null) {
//            for (String contentType : preference.getPreferredContentTypes()) {
//                System.out.println("Processing content type: " + contentType);
//
//                switch (contentType.toLowerCase()) {
//                    case "movie":
//                        List<MovieRecommendationDTO> movies = getGenreBasedMovies(preference.getPreferredGenres());
//                        System.out.println("Found " + movies.size() + " movies");
//                        recommendations.put("movies", movies);
//                        break;
//                    case "novel":
//                        List<NovelRecommendationDTO> novels = getGenreBasedNovels(preference.getPreferredGenres());
//                        System.out.println("Found " + novels.size() + " novels");
//                        recommendations.put("novels", novels);
//                        break;
//                    case "webtoon":
//                        List<WebtoonRecommendationDTO> webtoons = getGenreBasedWebtoons(preference.getPreferredGenres());
//                        System.out.println("Found " + webtoons.size() + " webtoons");
//                        recommendations.put("webtoons", webtoons);
//                        break;
//                    case "ott":
//                        List<OTTRecommendationDTO> ott = getGenreBasedOTT(preference.getPreferredGenres());
//                        System.out.println("Found " + ott.size() + " ott contents");
//                        recommendations.put("ott", ott);
//                        break;
//                    case "game":
//                        List<GameRecommendationDTO> games = getGenreBasedGames(preference.getPreferredGenres());
//                        System.out.println("Found " + games.size() + " games");
//                        recommendations.put("games", games);
//                        break;
//                }
//            }
//        }
//
//        // 추천이 비어있다면 기본 추천 제공
//        if (recommendations.isEmpty()) {
//            System.out.println("Recommendations empty, returning welcome recommendations");
//            return getWelcomeRecommendations();
//        }
//
//        System.out.println("Final recommendations: " + recommendations.keySet());
//        System.out.println("=== getInitialRecommendations DEBUG END ===");
//        return recommendations;
//    }
//
//    // CGV 영화 추천 (DTO 변환)
//    private List<MovieRecommendationDTO> getGenreBasedMovies(List<String> genres) {
//        System.out.println("Getting genre-based movies for genres: " + genres);
//        try {
//            long totalMovies = movieRepository.count();
//            System.out.println("Total movies in CGV repository: " + totalMovies);
//
//            List<?> allMovies = movieRepository.findAll();
//            List<?> filteredMovies;
//
//            if (genres == null || genres.isEmpty()) {
//                filteredMovies = allMovies.stream().limit(8).collect(Collectors.toList());
//                System.out.println("No genres specified, returning " + filteredMovies.size() + " movies");
//            } else {
//                filteredMovies = allMovies.stream()
//                        .filter(movie -> isContentMatchingGenres(movie, genres))
//                        .limit(8)
//                        .collect(Collectors.toList());
//
//                if (filteredMovies.isEmpty()) {
//                    filteredMovies = allMovies.stream().limit(8).collect(Collectors.toList());
//                    System.out.println("No genre matches found, returning first " + filteredMovies.size() + " movies");
//                } else {
//                    System.out.println("Genre-filtered movies: " + filteredMovies.size());
//                }
//            }
//
//            // Entity를 DTO로 변환
//            return filteredMovies.stream()
//                    .map(this::convertToMovieDTO)
//                    .collect(Collectors.toList());
//
//        } catch (Exception e) {
//            System.err.println("Error getting movies: " + e.getMessage());
//            e.printStackTrace();
//            return new ArrayList<>();
//        }
//    }
//
//    // 소설 추천 (DTO 변환)
//    private List<NovelRecommendationDTO> getGenreBasedNovels(List<String> genres) {
//        try {
//            List<?> allNovels = novelRepository.findAll();
//            List<?> filteredNovels;
//
//            if (genres == null || genres.isEmpty()) {
//                filteredNovels = allNovels.stream().limit(8).collect(Collectors.toList());
//            } else {
//                filteredNovels = allNovels.stream()
//                        .filter(novel -> isContentMatchingGenres(novel, genres))
//                        .limit(8)
//                        .collect(Collectors.toList());
//
//                if (filteredNovels.isEmpty()) {
//                    filteredNovels = allNovels.stream().limit(8).collect(Collectors.toList());
//                }
//            }
//
//            return filteredNovels.stream()
//                    .map(this::convertToNovelDTO)
//                    .collect(Collectors.toList());
//
//        } catch (Exception e) {
//            System.err.println("Error getting novels: " + e.getMessage());
//            return new ArrayList<>();
//        }
//    }
//
//    // 웹툰 추천 (DTO 변환)
//    private List<WebtoonRecommendationDTO> getGenreBasedWebtoons(List<String> genres) {
//        try {
//            List<?> allWebtoons = webtoonRepository.findAll();
//            List<?> filteredWebtoons;
//
//            if (genres == null || genres.isEmpty()) {
//                filteredWebtoons = allWebtoons.stream().limit(8).collect(Collectors.toList());
//            } else {
//                filteredWebtoons = allWebtoons.stream()
//                        .filter(webtoon -> isContentMatchingGenres(webtoon, genres))
//                        .limit(8)
//                        .collect(Collectors.toList());
//
//                if (filteredWebtoons.isEmpty()) {
//                    filteredWebtoons = allWebtoons.stream().limit(8).collect(Collectors.toList());
//                }
//            }
//
//            return filteredWebtoons.stream()
//                    .map(this::convertToWebtoonDTO)
//                    .collect(Collectors.toList());
//
//        } catch (Exception e) {
//            System.err.println("Error getting webtoons: " + e.getMessage());
//            return new ArrayList<>();
//        }
//    }
//
//    // OTT 추천 (DTO 변환)
//    private List<OTTRecommendationDTO> getGenreBasedOTT(List<String> genres) {
//        try {
//            List<?> allOTT = netflixRepository.findAll();
//            List<?> filteredOTT;
//
//            if (genres == null || genres.isEmpty()) {
//                filteredOTT = allOTT.stream().limit(8).collect(Collectors.toList());
//            } else {
//                filteredOTT = allOTT.stream()
//                        .filter(ott -> isContentMatchingGenres(ott, genres))
//                        .limit(8)
//                        .collect(Collectors.toList());
//
//                if (filteredOTT.isEmpty()) {
//                    filteredOTT = allOTT.stream().limit(8).collect(Collectors.toList());
//                }
//            }
//
//            return filteredOTT.stream()
//                    .map(this::convertToOTTDTO)
//                    .collect(Collectors.toList());
//
//        } catch (Exception e) {
//            System.err.println("Error getting OTT: " + e.getMessage());
//            return new ArrayList<>();
//        }
//    }
//
//    // 게임 추천 (DTO 변환)
//    private List<GameRecommendationDTO> getGenreBasedGames(List<String> genres) {
//        try {
//            List<?> allGames = gameRepository.findAll();
//            List<?> filteredGames;
//
//            if (genres == null || genres.isEmpty()) {
//                filteredGames = allGames.stream().limit(8).collect(Collectors.toList());
//            } else {
//                filteredGames = allGames.stream()
//                        .filter(game -> isContentMatchingGenres(game, genres))
//                        .limit(8)
//                        .collect(Collectors.toList());
//
//                if (filteredGames.isEmpty()) {
//                    filteredGames = allGames.stream().limit(8).collect(Collectors.toList());
//                }
//            }
//
//            return filteredGames.stream()
//                    .map(this::convertToGameDTO)
//                    .collect(Collectors.toList());
//
//        } catch (Exception e) {
//            System.err.println("Error getting games: " + e.getMessage());
//            return new ArrayList<>();
//        }
//    }
//
//    // DTO 변환 메서드들 (TraditionalRecommendationService와 동일)
//    private MovieRecommendationDTO convertToMovieDTO(Object movie) {
//        MovieRecommendationDTO dto = new MovieRecommendationDTO();
//        try {
//            dto.setId(getIdFromObject(movie));
//            dto.setTitle(getFieldValue(movie, "title", String.class));
//            dto.setDirector(getFieldValue(movie, "director", String.class));
//            dto.setThumbnailUrl(getFieldValue(movie, "thumbnailUrl", String.class));
//            dto.setRunningTime(getFieldValue(movie, "runningTime", Integer.class));
//            dto.setRating(getFieldValue(movie, "rating", String.class));
//            dto.setAgeRating(getFieldValue(movie, "ageRating", String.class));
//
//            Object releaseDate = getFieldValue(movie, "releaseDate", Object.class);
//            if (releaseDate != null) {
//                dto.setReleaseDate(releaseDate.toString());
//            }
//
//            dto.setGenres(new ArrayList<>());
//            dto.setActors(new ArrayList<>());
//
//        } catch (Exception e) {
//            System.err.println("Error converting movie to DTO: " + e.getMessage());
//        }
//        return dto;
//    }
//
//    private NovelRecommendationDTO convertToNovelDTO(Object novel) {
//        NovelRecommendationDTO dto = new NovelRecommendationDTO();
//        try {
//            dto.setId(getIdFromObject(novel));
//            dto.setTitle(getFieldValue(novel, "title", String.class));
//            dto.setThumbnail(getFieldValue(novel, "thumbnail", String.class));
//            dto.setSummary(getFieldValue(novel, "summary", String.class));
//            dto.setUrl(getFieldValue(novel, "url", String.class));
//            dto.setGenres(new ArrayList<>());
//            dto.setAuthors(new ArrayList<>());
//        } catch (Exception e) {
//            System.err.println("Error converting novel to DTO: " + e.getMessage());
//        }
//        return dto;
//    }
//
//    private WebtoonRecommendationDTO convertToWebtoonDTO(Object webtoon) {
//        WebtoonRecommendationDTO dto = new WebtoonRecommendationDTO();
//        try {
//            dto.setId(getIdFromObject(webtoon));
//            dto.setTitle(getFieldValue(webtoon, "title", String.class));
//            dto.setThumbnail(getFieldValue(webtoon, "thumbnail", String.class));
//            dto.setSummary(getFieldValue(webtoon, "summary", String.class));
//            dto.setUrl(getFieldValue(webtoon, "url", String.class));
//
//            Object publishDate = getFieldValue(webtoon, "publishDate", Object.class);
//            if (publishDate != null) {
//                dto.setPublishDate(publishDate.toString());
//            }
//
//            dto.setGenres(new ArrayList<>());
//            dto.setAuthors(new ArrayList<>());
//        } catch (Exception e) {
//            System.err.println("Error converting webtoon to DTO: " + e.getMessage());
//        }
//        return dto;
//    }
//
//    private OTTRecommendationDTO convertToOTTDTO(Object ott) {
//        OTTRecommendationDTO dto = new OTTRecommendationDTO();
//        try {
//            dto.setId(getIdFromObject(ott));
//            dto.setTitle(getFieldValue(ott, "title", String.class));
//            dto.setDescription(getFieldValue(ott, "description", String.class));
//            dto.setThumbnailUrl(getFieldValue(ott, "thumbnailUrl", String.class));
//
//            Object releaseDate = getFieldValue(ott, "releaseDate", Object.class);
//            if (releaseDate != null) {
//                dto.setReleaseDate(releaseDate.toString());
//            }
//
//            dto.setGenres(new ArrayList<>());
//            dto.setFeatures(new ArrayList<>());
//            dto.setActors(new ArrayList<>());
//        } catch (Exception e) {
//            System.err.println("Error converting OTT to DTO: " + e.getMessage());
//        }
//        return dto;
//    }
//
//    private GameRecommendationDTO convertToGameDTO(Object game) {
//        GameRecommendationDTO dto = new GameRecommendationDTO();
//        try {
//            dto.setId(getIdFromObject(game));
//            dto.setTitle(getFieldValue(game, "title", String.class));
//            dto.setDescription(getFieldValue(game, "description", String.class));
//            dto.setThumbnailUrl(getFieldValue(game, "thumbnailUrl", String.class));
//
//            Object releaseDate = getFieldValue(game, "releaseDate", Object.class);
//            if (releaseDate != null) {
//                dto.setReleaseDate(releaseDate.toString());
//            }
//
//            dto.setGenres(new ArrayList<>());
//            dto.setCategories(new ArrayList<>());
//            dto.setDevelopers(new ArrayList<>());
//            dto.setPublishers(new ArrayList<>());
//        } catch (Exception e) {
//            System.err.println("Error converting game to DTO: " + e.getMessage());
//        }
//        return dto;
//    }
//
//    // 유틸리티 메서드들
//    private <T> T getFieldValue(Object obj, String fieldName, Class<T> type) {
//        try {
//            java.lang.reflect.Field field = obj.getClass().getDeclaredField(fieldName);
//            field.setAccessible(true);
//            Object value = field.get(obj);
//            return type.cast(value);
//        } catch (Exception e) {
//            return null;
//        }
//    }
//
//    private Long getIdFromObject(Object obj) {
//        try {
//            String[] possibleIdFields = {"id", "contentId", "movieId", "novelId", "webtoonId", "gameId"};
//
//            for (String fieldName : possibleIdFields) {
//                try {
//                    java.lang.reflect.Field idField = obj.getClass().getDeclaredField(fieldName);
//                    idField.setAccessible(true);
//                    Object idValue = idField.get(obj);
//
//                    if (idValue != null) {
//                        if (idValue instanceof Long) {
//                            return (Long) idValue;
//                        } else if (idValue instanceof Integer) {
//                            return ((Integer) idValue).longValue();
//                        } else if (idValue instanceof String) {
//                            try {
//                                return Long.parseLong((String) idValue);
//                            } catch (NumberFormatException e) {
//                                continue;
//                            }
//                        }
//                    }
//                } catch (NoSuchFieldException e) {
//                    continue;
//                }
//            }
//        } catch (Exception e) {
//            System.err.println("Error extracting ID from object: " + e.getMessage());
//        }
//        return 0L;
//    }
//
//    private boolean isContentMatchingGenres(Object content, List<String> preferredGenres) {
//        try {
//            String[] possibleGenreFields = {"genres", "genre", "genreList", "categories", "category"};
//
//            for (String fieldName : possibleGenreFields) {
//                try {
//                    java.lang.reflect.Field genreField = content.getClass().getDeclaredField(fieldName);
//                    genreField.setAccessible(true);
//                    Object genreValue = genreField.get(content);
//
//                    if (genreValue != null) {
//                        List<String> contentGenres = new ArrayList<>();
//
//                        if (genreValue instanceof List) {
//                            List<?> genreList = (List<?>) genreValue;
//                            for (Object genreItem : genreList) {
//                                if (genreItem instanceof String) {
//                                    contentGenres.add((String) genreItem);
//                                } else {
//                                    try {
//                                        java.lang.reflect.Field nameField = genreItem.getClass().getDeclaredField("name");
//                                        nameField.setAccessible(true);
//                                        String name = (String) nameField.get(genreItem);
//                                        if (name != null) {
//                                            contentGenres.add(name);
//                                        }
//                                    } catch (Exception e) {
//                                        contentGenres.add(genreItem.toString());
//                                    }
//                                }
//                            }
//                        } else if (genreValue instanceof String) {
//                            contentGenres.add((String) genreValue);
//                        }
//
//                        if (!contentGenres.isEmpty() && preferredGenres != null) {
//                            return contentGenres.stream()
//                                    .anyMatch(genre -> preferredGenres.stream()
//                                            .anyMatch(preferred ->
//                                                    preferred.toLowerCase().contains(genre.toLowerCase()) ||
//                                                            genre.toLowerCase().contains(preferred.toLowerCase())));
//                        }
//                    }
//                } catch (NoSuchFieldException e) {
//                    continue;
//                }
//            }
//        } catch (Exception e) {
//            System.out.println("Could not extract genres from " + content.getClass().getSimpleName());
//        }
//        return true; // 장르 정보를 찾을 수 없으면 모든 콘텐츠 포함
//    }
//
//    // 기본 추천 (DTO 변환)
//    private Map<String, List<?>> getWelcomeRecommendations() {
//        System.out.println("Getting welcome recommendations from platform tables");
//        Map<String, List<?>> recommendations = new HashMap<>();
//
//        try {
//            List<MovieRecommendationDTO> movies = movieRepository.findAll().stream()
//                    .limit(5)
//                    .map(this::convertToMovieDTO)
//                    .collect(Collectors.toList());
//
//            List<NovelRecommendationDTO> novels = novelRepository.findAll().stream()
//                    .limit(5)
//                    .map(this::convertToNovelDTO)
//                    .collect(Collectors.toList());
//
//            List<WebtoonRecommendationDTO> webtoons = webtoonRepository.findAll().stream()
//                    .limit(5)
//                    .map(this::convertToWebtoonDTO)
//                    .collect(Collectors.toList());
//
//            List<OTTRecommendationDTO> ott = netflixRepository.findAll().stream()
//                    .limit(5)
//                    .map(this::convertToOTTDTO)
//                    .collect(Collectors.toList());
//
//            List<GameRecommendationDTO> games = gameRepository.findAll().stream()
//                    .limit(5)
//                    .map(this::convertToGameDTO)
//                    .collect(Collectors.toList());
//
//            recommendations.put("movies", movies);
//            recommendations.put("novels", novels);
//            recommendations.put("webtoons", webtoons);
//            recommendations.put("ott", ott);
//            recommendations.put("games", games);
//
//            System.out.println("Welcome recommendations - Movies: " + movies.size() +
//                    ", Novels: " + novels.size() +
//                    ", Webtoons: " + webtoons.size() +
//                    ", OTT: " + ott.size() +
//                    ", Games: " + games.size());
//
//        } catch (Exception e) {
//            System.err.println("Error getting welcome recommendations: " + e.getMessage());
//            e.printStackTrace();
//        }
//
//        return recommendations;
//    }
//
//    // 기존 메서드들 (변경 없음)
//    @Transactional
//    public UserPreference createInitialPreference(String username, SignUpRequest signUpRequest) {
//        UserPreference preference = new UserPreference();
//        preference.setUsername(username);
//
//        if (signUpRequest.getPreferredGenres() != null) {
//            preference.setPreferredGenres(signUpRequest.getPreferredGenres());
//        }
//
//        if (signUpRequest.getPreferredContentTypes() != null) {
//            preference.setPreferredContentTypes(signUpRequest.getPreferredContentTypes());
//        }
//
//        preference.setAgeGroup(signUpRequest.getAgeGroup());
//        preference.setPreferredAgeRating(signUpRequest.getPreferredAgeRating());
//        preference.setFavoriteDirectors(signUpRequest.getFavoriteDirectors());
//        preference.setFavoriteAuthors(signUpRequest.getFavoriteAuthors());
//        preference.setFavoriteActors(signUpRequest.getFavoriteActors());
//        preference.setLikesNewContent(signUpRequest.getLikesNewContent());
//        preference.setLikesClassicContent(signUpRequest.getLikesClassicContent());
//        preference.setAdditionalNotes(signUpRequest.getAdditionalNotes());
//
//        return userPreferenceRepository.save(preference);
//    }
//
//    public Optional<UserPreference> getUserPreference(String username) {
//        return userPreferenceRepository.findByUsername(username);
//    }
//
//    @Transactional
//    public UserPreference updateUserPreference(String username, UserPreference updatedPreference) {
//        Optional<UserPreference> existingOpt = userPreferenceRepository.findByUsername(username);
//
//        if (existingOpt.isPresent()) {
//            UserPreference existing = existingOpt.get();
//
//            if (updatedPreference.getPreferredGenres() != null) {
//                existing.setPreferredGenres(updatedPreference.getPreferredGenres());
//            }
//            if (updatedPreference.getPreferredContentTypes() != null) {
//                existing.setPreferredContentTypes(updatedPreference.getPreferredContentTypes());
//            }
//            if (updatedPreference.getAgeGroup() != null) {
//                existing.setAgeGroup(updatedPreference.getAgeGroup());
//            }
//            if (updatedPreference.getPreferredAgeRating() != null) {
//                existing.setPreferredAgeRating(updatedPreference.getPreferredAgeRating());
//            }
//            if (updatedPreference.getFavoriteDirectors() != null) {
//                existing.setFavoriteDirectors(updatedPreference.getFavoriteDirectors());
//            }
//            if (updatedPreference.getFavoriteAuthors() != null) {
//                existing.setFavoriteAuthors(updatedPreference.getFavoriteAuthors());
//            }
//            if (updatedPreference.getFavoriteActors() != null) {
//                existing.setFavoriteActors(updatedPreference.getFavoriteActors());
//            }
//            if (updatedPreference.getLikesNewContent() != null) {
//                existing.setLikesNewContent(updatedPreference.getLikesNewContent());
//            }
//            if (updatedPreference.getLikesClassicContent() != null) {
//                existing.setLikesClassicContent(updatedPreference.getLikesClassicContent());
//            }
//            if (updatedPreference.getAdditionalNotes() != null) {
//                existing.setAdditionalNotes(updatedPreference.getAdditionalNotes());
//            }
//
//            return userPreferenceRepository.save(existing);
//        } else {
//            updatedPreference.setUsername(username);
//            return userPreferenceRepository.save(updatedPreference);
//        }
//    }
//
//    // 평점 기반 선호도 업데이트
//    @Transactional
//    public void updatePreferencesBasedOnRatings(String username) {
//        try {
//            List<ContentRating> ratings = contentRatingRepository.findByUsername(username);
//            updatePreferencesBasedOnRatings(username, ratings);
//        } catch (Exception e) {
//            System.err.println("Error updating preferences based on ratings for user " + username + ": " + e.getMessage());
//        }
//    }
//
//    @Transactional
//    public void updatePreferencesBasedOnRatings(String username, List<ContentRating> ratings) {
//        try {
//            Optional<UserPreference> preferenceOpt = userPreferenceRepository.findByUsername(username);
//            UserPreference preference = preferenceOpt.orElse(new UserPreference());
//
//            if (preference.getUsername() == null) {
//                preference.setUsername(username);
//            }
//
//            List<String> likedGenres = new ArrayList<>();
//            List<String> likedContentTypes = new ArrayList<>();
//
//            for (ContentRating rating : ratings) {
//                if (rating.getRating() != null && rating.getRating() >= 4.0) {
//                    if (!likedContentTypes.contains(rating.getContentType())) {
//                        likedContentTypes.add(rating.getContentType());
//                    }
//
//                    List<String> contentGenres = extractGenresFromContent(rating.getContentType(), rating.getContentId());
//                    for (String genre : contentGenres) {
//                        if (!likedGenres.contains(genre)) {
//                            likedGenres.add(genre);
//                        }
//                    }
//                }
//            }
//
//            if (preference.getPreferredGenres() == null) {
//                preference.setPreferredGenres(new ArrayList<>());
//            }
//            if (preference.getPreferredContentTypes() == null) {
//                preference.setPreferredContentTypes(new ArrayList<>());
//            }
//
//            for (String genre : likedGenres) {
//                if (!preference.getPreferredGenres().contains(genre)) {
//                    preference.getPreferredGenres().add(genre);
//                }
//            }
//
//            for (String contentType : likedContentTypes) {
//                if (!preference.getPreferredContentTypes().contains(contentType)) {
//                    preference.getPreferredContentTypes().add(contentType);
//                }
//            }
//
//            userPreferenceRepository.save(preference);
//
//        } catch (Exception e) {
//            System.err.println("Error updating preferences based on ratings for user " + username + ": " + e.getMessage());
//        }
//    }
//
//    private List<String> extractGenresFromContent(String contentType, Long contentId) {
//        List<String> genres = new ArrayList<>();
//
//        try {
//            switch (contentType.toLowerCase()) {
//                case "movie":
//                    movieRepository.findById(contentId).ifPresent(movie -> {
//                        genres.addAll(extractGenresFromObject(movie));
//                    });
//                    break;
//
//                case "novel":
//                    novelRepository.findById(contentId).ifPresent(novel -> {
//                        genres.addAll(extractGenresFromObject(novel));
//                    });
//                    break;
//
//                case "webtoon":
//                    webtoonRepository.findById(contentId).ifPresent(webtoon -> {
//                        genres.addAll(extractGenresFromObject(webtoon));
//                    });
//                    break;
//
//                case "ott":
//                    netflixRepository.findById(contentId).ifPresent(ott -> {
//                        genres.addAll(extractGenresFromObject(ott));
//                    });
//                    break;
//
//                case "game":
//                    gameRepository.findById(contentId).ifPresent(game -> {
//                        genres.addAll(extractGenresFromObject(game));
//                    });
//                    break;
//
//                default:
//                    break;
//            }
//        } catch (Exception e) {
//            System.err.println("Error extracting genres for " + contentType + " with ID " + contentId + ": " + e.getMessage());
//        }
//
//        return genres;
//    }
//
//    private List<String> extractGenresFromObject(Object content) {
//        List<String> genres = new ArrayList<>();
//
//        try {
//            String[] possibleGenreFields = {"genres", "genre", "genreList", "categories", "category"};
//
//            for (String fieldName : possibleGenreFields) {
//                try {
//                    java.lang.reflect.Field genreField = content.getClass().getDeclaredField(fieldName);
//                    genreField.setAccessible(true);
//                    Object genreValue = genreField.get(content);
//
//                    if (genreValue != null) {
//                        if (genreValue instanceof List) {
//                            List<?> genreList = (List<?>) genreValue;
//                            for (Object genreItem : genreList) {
//                                if (genreItem instanceof String) {
//                                    genres.add((String) genreItem);
//                                } else {
//                                    try {
//                                        java.lang.reflect.Field nameField = genreItem.getClass().getDeclaredField("name");
//                                        nameField.setAccessible(true);
//                                        String name = (String) nameField.get(genreItem);
//                                        if (name != null) {
//                                            genres.add(name);
//                                        }
//                                    } catch (Exception e) {
//                                        genres.add(genreItem.toString());
//                                    }
//                                }
//                            }
//                        } else if (genreValue instanceof String) {
//                            genres.add((String) genreValue);
//                        }
//                        break;
//                    }
//                } catch (NoSuchFieldException e) {
//                    continue;
//                }
//            }
//        } catch (Exception e) {
//            System.err.println("Error extracting genres from object: " + e.getMessage());
//        }
//
//        return genres;
//    }
//}