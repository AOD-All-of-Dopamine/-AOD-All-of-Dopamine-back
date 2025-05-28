package com.example.AOD.recommendation.service;

import com.example.AOD.recommendation.domain.UserPreference;
import com.example.AOD.recommendation.domain.ContentRating;
import com.example.AOD.recommendation.repository.UserPreferenceRepository;
import com.example.AOD.recommendation.repository.ContentRatingRepository;
import com.example.AOD.recommendation.dto.*;

// 플랫폼별 리포지토리 import
import com.example.AOD.movie.CGV.repository.MovieRepository;
import com.example.AOD.Novel.NaverSeriesNovel.repository.NaverSeriesNovelRepository;
import com.example.AOD.Webtoon.NaverWebtoon.repository.WebtoonRepository;
import com.example.AOD.OTT.Netflix.repository.NetflixContentRepository;
import com.example.AOD.game.StreamAPI.repository.GameRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class TraditionalRecommendationService {

    @Autowired
    private UserPreferenceRepository userPreferenceRepository;

    @Autowired
    private ContentRatingRepository contentRatingRepository;

    // 플랫폼별 리포지토리들
    @Autowired
    private MovieRepository movieRepository;

    @Autowired
    private NaverSeriesNovelRepository novelRepository;

    @Autowired
    private WebtoonRepository webtoonRepository;

    @Autowired
    private NetflixContentRepository netflixRepository;

    @Autowired
    private GameRepository gameRepository;

    public Map<String, List<?>> getRecommendationsForUser(String username) {
        System.out.println("=== Traditional Recommendation DEBUG START ===");
        System.out.println("Username: " + username);

        Map<String, List<?>> recommendations = new HashMap<>();

        try {
            Optional<UserPreference> preferenceOpt = userPreferenceRepository.findByUsername(username);
            System.out.println("User preference found: " + preferenceOpt.isPresent());

            if (preferenceOpt.isEmpty()) {
                System.out.println("No preferences found, returning default recommendations");
                return getDefaultRecommendations();
            }

            UserPreference preference = preferenceOpt.get();
            System.out.println("Preferred content types: " + preference.getPreferredContentTypes());

            // NULL 체크 추가
            if (preference.getPreferredContentTypes() == null || preference.getPreferredContentTypes().isEmpty()) {
                System.out.println("Preferred content types is null or empty, returning default recommendations");
                return getDefaultRecommendations();
            }

            List<ContentRating> userRatings = contentRatingRepository.findByUsername(username);
            System.out.println("User ratings count: " + userRatings.size());

            // 각 콘텐츠 타입별로 추천 (DTO로 변환하여 반환)
            for (String contentType : preference.getPreferredContentTypes()) {
                System.out.println("Processing content type: " + contentType);

                try {
                    switch (contentType.toLowerCase()) {
                        case "movie":
                            List<MovieRecommendationDTO> movies = getMovieRecommendations(preference, userRatings);
                            System.out.println("Found " + movies.size() + " movie recommendations");
                            recommendations.put("movies", movies);
                            break;
                        case "novel":
                            List<NovelRecommendationDTO> novels = getNovelRecommendations(preference, userRatings);
                            System.out.println("Found " + novels.size() + " novel recommendations");
                            recommendations.put("novels", novels);
                            break;
                        case "webtoon":
                            List<WebtoonRecommendationDTO> webtoons = getWebtoonRecommendations(preference, userRatings);
                            System.out.println("Found " + webtoons.size() + " webtoon recommendations");
                            recommendations.put("webtoons", webtoons);
                            break;
                        case "ott":
                            List<OTTRecommendationDTO> ott = getOTTRecommendations(preference, userRatings);
                            System.out.println("Found " + ott.size() + " ott recommendations");
                            recommendations.put("ott", ott);
                            break;
                        case "game":
                            List<GameRecommendationDTO> games = getGameRecommendations(preference, userRatings);
                            System.out.println("Found " + games.size() + " game recommendations");
                            recommendations.put("games", games);
                            break;
                        default:
                            System.out.println("Unknown content type: " + contentType);
                            break;
                    }
                } catch (Exception e) {
                    System.err.println("Error processing content type " + contentType + ": " + e.getMessage());
                    e.printStackTrace();
                }
            }

            System.out.println("Final recommendations keys: " + recommendations.keySet());
            System.out.println("=== Traditional Recommendation DEBUG END ===");

            return recommendations;

        } catch (Exception e) {
            System.err.println("Error in getRecommendationsForUser: " + e.getMessage());
            e.printStackTrace();
            return getDefaultRecommendations();
        }
    }

    // 영화 추천 (DTO 변환)
    private List<MovieRecommendationDTO> getMovieRecommendations(UserPreference preference, List<ContentRating> userRatings) {
        try {
            System.out.println("Getting movie recommendations...");

            Set<Long> ratedMovieIds = userRatings.stream()
                    .filter(r -> "movie".equals(r.getContentType()))
                    .map(ContentRating::getContentId)
                    .collect(Collectors.toSet());

            System.out.println("User has rated " + ratedMovieIds.size() + " movies");

            List<?> allMovies = movieRepository.findAll();
            System.out.println("Total movies in database: " + allMovies.size());

            if (allMovies.isEmpty()) {
                return new ArrayList<>();
            }

            // Entity를 DTO로 변환
            return allMovies.stream()
                    .filter(movie -> {
                        Long movieId = getIdFromObject(movie);
                        return !ratedMovieIds.contains(movieId);
                    })
                    .filter(movie -> isContentMatchingPreference(movie, preference))
                    .limit(10)
                    .map(this::convertToMovieDTO)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            System.err.println("Error in getMovieRecommendations: " + e.getMessage());
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    // 소설 추천 (DTO 변환)
    private List<NovelRecommendationDTO> getNovelRecommendations(UserPreference preference, List<ContentRating> userRatings) {
        try {
            Set<Long> ratedNovelIds = userRatings.stream()
                    .filter(r -> "novel".equals(r.getContentType()))
                    .map(ContentRating::getContentId)
                    .collect(Collectors.toSet());

            List<?> allNovels = novelRepository.findAll();

            if (allNovels.isEmpty()) {
                return new ArrayList<>();
            }

            return allNovels.stream()
                    .filter(novel -> {
                        Long novelId = getIdFromObject(novel);
                        return !ratedNovelIds.contains(novelId);
                    })
                    .filter(novel -> isContentMatchingPreference(novel, preference))
                    .limit(10)
                    .map(this::convertToNovelDTO)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            System.err.println("Error in getNovelRecommendations: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    // 웹툰 추천 (DTO 변환)
    private List<WebtoonRecommendationDTO> getWebtoonRecommendations(UserPreference preference, List<ContentRating> userRatings) {
        try {
            Set<Long> ratedWebtoonIds = userRatings.stream()
                    .filter(r -> "webtoon".equals(r.getContentType()))
                    .map(ContentRating::getContentId)
                    .collect(Collectors.toSet());

            List<?> allWebtoons = webtoonRepository.findAll();

            if (allWebtoons.isEmpty()) {
                return new ArrayList<>();
            }

            return allWebtoons.stream()
                    .filter(webtoon -> {
                        Long webtoonId = getIdFromObject(webtoon);
                        return !ratedWebtoonIds.contains(webtoonId);
                    })
                    .filter(webtoon -> isContentMatchingPreference(webtoon, preference))
                    .limit(10)
                    .map(this::convertToWebtoonDTO)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            System.err.println("Error in getWebtoonRecommendations: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    // OTT 추천 (DTO 변환)
    private List<OTTRecommendationDTO> getOTTRecommendations(UserPreference preference, List<ContentRating> userRatings) {
        try {
            Set<Long> ratedOTTIds = userRatings.stream()
                    .filter(r -> "ott".equals(r.getContentType()))
                    .map(ContentRating::getContentId)
                    .collect(Collectors.toSet());

            List<?> allOTT = netflixRepository.findAll();

            if (allOTT.isEmpty()) {
                return new ArrayList<>();
            }

            return allOTT.stream()
                    .filter(ott -> {
                        Long ottId = getIdFromObject(ott);
                        return !ratedOTTIds.contains(ottId);
                    })
                    .filter(ott -> isContentMatchingPreference(ott, preference))
                    .limit(10)
                    .map(this::convertToOTTDTO)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            System.err.println("Error in getOTTRecommendations: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    // 게임 추천 (DTO 변환)
    private List<GameRecommendationDTO> getGameRecommendations(UserPreference preference, List<ContentRating> userRatings) {
        try {
            Set<Long> ratedGameIds = userRatings.stream()
                    .filter(r -> "game".equals(r.getContentType()))
                    .map(ContentRating::getContentId)
                    .collect(Collectors.toSet());

            List<?> allGames = gameRepository.findAll();

            if (allGames.isEmpty()) {
                return new ArrayList<>();
            }

            return allGames.stream()
                    .filter(game -> {
                        Long gameId = getIdFromObject(game);
                        return !ratedGameIds.contains(gameId);
                    })
                    .filter(game -> isContentMatchingPreference(game, preference))
                    .limit(10)
                    .map(this::convertToGameDTO)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            System.err.println("Error in getGameRecommendations: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    // DTO 변환 메서드들
    private MovieRecommendationDTO convertToMovieDTO(Object movie) {
        MovieRecommendationDTO dto = new MovieRecommendationDTO();
        try {
            dto.setId(getIdFromObject(movie));
            dto.setTitle(getFieldValue(movie, "title", String.class));
            dto.setDirector(getFieldValue(movie, "director", String.class));
            dto.setThumbnailUrl(getFieldValue(movie, "thumbnailUrl", String.class));
            dto.setRunningTime(getFieldValue(movie, "runningTime", Integer.class));
            dto.setRating(getFieldValue(movie, "rating", String.class));
            dto.setAgeRating(getFieldValue(movie, "ageRating", String.class));

            // 날짜 필드 안전하게 처리
            Object releaseDate = getFieldValue(movie, "releaseDate", Object.class);
            if (releaseDate != null) {
                dto.setReleaseDate(releaseDate.toString());
            }

            // 연관 관계는 제외하고 간단한 정보만
            dto.setGenres(new ArrayList<>());
            dto.setActors(new ArrayList<>());

        } catch (Exception e) {
            System.err.println("Error converting movie to DTO: " + e.getMessage());
        }
        return dto;
    }

    private NovelRecommendationDTO convertToNovelDTO(Object novel) {
        NovelRecommendationDTO dto = new NovelRecommendationDTO();
        try {
            dto.setId(getIdFromObject(novel));
            dto.setTitle(getFieldValue(novel, "title", String.class));
            dto.setThumbnail(getFieldValue(novel, "thumbnail", String.class));
            dto.setSummary(getFieldValue(novel, "summary", String.class));
            dto.setUrl(getFieldValue(novel, "url", String.class));
            dto.setGenres(new ArrayList<>());
            dto.setAuthors(new ArrayList<>());
        } catch (Exception e) {
            System.err.println("Error converting novel to DTO: " + e.getMessage());
        }
        return dto;
    }

    private WebtoonRecommendationDTO convertToWebtoonDTO(Object webtoon) {
        WebtoonRecommendationDTO dto = new WebtoonRecommendationDTO();
        try {
            dto.setId(getIdFromObject(webtoon));
            dto.setTitle(getFieldValue(webtoon, "title", String.class));
            dto.setThumbnail(getFieldValue(webtoon, "thumbnail", String.class));
            dto.setSummary(getFieldValue(webtoon, "summary", String.class));
            dto.setUrl(getFieldValue(webtoon, "url", String.class));

            Object publishDate = getFieldValue(webtoon, "publishDate", Object.class);
            if (publishDate != null) {
                dto.setPublishDate(publishDate.toString());
            }

            dto.setGenres(new ArrayList<>());
            dto.setAuthors(new ArrayList<>());
        } catch (Exception e) {
            System.err.println("Error converting webtoon to DTO: " + e.getMessage());
        }
        return dto;
    }

    private OTTRecommendationDTO convertToOTTDTO(Object ott) {
        OTTRecommendationDTO dto = new OTTRecommendationDTO();
        try {
            dto.setId(getIdFromObject(ott));
            dto.setTitle(getFieldValue(ott, "title", String.class));
            dto.setDescription(getFieldValue(ott, "description", String.class));
            dto.setThumbnailUrl(getFieldValue(ott, "thumbnailUrl", String.class));

            Object releaseDate = getFieldValue(ott, "releaseDate", Object.class);
            if (releaseDate != null) {
                dto.setReleaseDate(releaseDate.toString());
            }

            dto.setGenres(new ArrayList<>());
            dto.setFeatures(new ArrayList<>());
            dto.setActors(new ArrayList<>());
        } catch (Exception e) {
            System.err.println("Error converting OTT to DTO: " + e.getMessage());
        }
        return dto;
    }

    private GameRecommendationDTO convertToGameDTO(Object game) {
        GameRecommendationDTO dto = new GameRecommendationDTO();
        try {
            dto.setId(getIdFromObject(game));
            dto.setTitle(getFieldValue(game, "title", String.class));
            dto.setDescription(getFieldValue(game, "description", String.class));
            dto.setThumbnailUrl(getFieldValue(game, "thumbnailUrl", String.class));

            Object releaseDate = getFieldValue(game, "releaseDate", Object.class);
            if (releaseDate != null) {
                dto.setReleaseDate(releaseDate.toString());
            }

            dto.setGenres(new ArrayList<>());
            dto.setCategories(new ArrayList<>());
            dto.setDevelopers(new ArrayList<>());
            dto.setPublishers(new ArrayList<>());
        } catch (Exception e) {
            System.err.println("Error converting game to DTO: " + e.getMessage());
        }
        return dto;
    }

    // 유틸리티 메서드들
    private <T> T getFieldValue(Object obj, String fieldName, Class<T> type) {
        try {
            java.lang.reflect.Field field = obj.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            Object value = field.get(obj);
            return type.cast(value);
        } catch (Exception e) {
            return null;
        }
    }

    private Long getIdFromObject(Object obj) {
        try {
            String[] possibleIdFields = {"id", "contentId", "movieId", "novelId", "webtoonId", "gameId"};

            for (String fieldName : possibleIdFields) {
                try {
                    java.lang.reflect.Field idField = obj.getClass().getDeclaredField(fieldName);
                    idField.setAccessible(true);
                    Object idValue = idField.get(obj);

                    if (idValue != null) {
                        if (idValue instanceof Long) {
                            return (Long) idValue;
                        } else if (idValue instanceof Integer) {
                            return ((Integer) idValue).longValue();
                        } else if (idValue instanceof String) {
                            try {
                                return Long.parseLong((String) idValue);
                            } catch (NumberFormatException e) {
                                continue;
                            }
                        }
                    }
                } catch (NoSuchFieldException e) {
                    continue;
                }
            }
        } catch (Exception e) {
            System.err.println("Error extracting ID from object: " + e.getMessage());
        }
        return 0L;
    }

    private boolean isContentMatchingPreference(Object content, UserPreference preference) {
        try {
            if (preference.getPreferredGenres() == null || preference.getPreferredGenres().isEmpty()) {
                return true;
            }

            String[] possibleGenreFields = {"genres", "genre", "genreList", "categories", "category"};

            for (String fieldName : possibleGenreFields) {
                try {
                    java.lang.reflect.Field genreField = content.getClass().getDeclaredField(fieldName);
                    genreField.setAccessible(true);
                    Object genreValue = genreField.get(content);

                    if (genreValue != null) {
                        List<String> contentGenres = new ArrayList<>();

                        if (genreValue instanceof List) {
                            List<?> genreList = (List<?>) genreValue;
                            for (Object genreItem : genreList) {
                                if (genreItem instanceof String) {
                                    contentGenres.add((String) genreItem);
                                } else {
                                    try {
                                        java.lang.reflect.Field nameField = genreItem.getClass().getDeclaredField("name");
                                        nameField.setAccessible(true);
                                        String name = (String) nameField.get(genreItem);
                                        if (name != null) {
                                            contentGenres.add(name);
                                        }
                                    } catch (Exception e) {
                                        contentGenres.add(genreItem.toString());
                                    }
                                }
                            }
                        } else if (genreValue instanceof String) {
                            contentGenres.add((String) genreValue);
                        }

                        if (!contentGenres.isEmpty()) {
                            boolean matches = contentGenres.stream()
                                    .anyMatch(genre -> preference.getPreferredGenres().stream()
                                            .anyMatch(preferred ->
                                                    preferred.toLowerCase().contains(genre.toLowerCase()) ||
                                                            genre.toLowerCase().contains(preferred.toLowerCase())));

                            if (matches) {
                                return true;
                            }
                        }
                    }
                } catch (NoSuchFieldException e) {
                    continue;
                }
            }
        } catch (Exception e) {
            System.err.println("Error matching preference: " + e.getMessage());
        }

        return true;
    }

    private Map<String, List<?>> getDefaultRecommendations() {
        System.out.println("Getting default recommendations...");
        Map<String, List<?>> recommendations = new HashMap<>();

        try {
            // 기본 추천도 DTO로 변환
            List<MovieRecommendationDTO> movies = movieRepository.findAll().stream()
                    .limit(5)
                    .map(this::convertToMovieDTO)
                    .collect(Collectors.toList());

            List<NovelRecommendationDTO> novels = novelRepository.findAll().stream()
                    .limit(5)
                    .map(this::convertToNovelDTO)
                    .collect(Collectors.toList());

            List<WebtoonRecommendationDTO> webtoons = webtoonRepository.findAll().stream()
                    .limit(5)
                    .map(this::convertToWebtoonDTO)
                    .collect(Collectors.toList());

            List<OTTRecommendationDTO> ott = netflixRepository.findAll().stream()
                    .limit(5)
                    .map(this::convertToOTTDTO)
                    .collect(Collectors.toList());

            List<GameRecommendationDTO> games = gameRepository.findAll().stream()
                    .limit(5)
                    .map(this::convertToGameDTO)
                    .collect(Collectors.toList());

            recommendations.put("movies", movies);
            recommendations.put("novels", novels);
            recommendations.put("webtoons", webtoons);
            recommendations.put("ott", ott);
            recommendations.put("games", games);

        } catch (Exception e) {
            System.err.println("Error getting default recommendations: " + e.getMessage());
            e.printStackTrace();
        }

        return recommendations;
    }
}