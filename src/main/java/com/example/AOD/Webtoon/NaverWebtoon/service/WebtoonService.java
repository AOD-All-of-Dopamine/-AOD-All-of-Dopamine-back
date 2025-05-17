package com.example.AOD.Webtoon.NaverWebtoon.service;

import com.example.AOD.Webtoon.NaverWebtoon.crawler.NaverWebtoonCrawler;
import com.example.AOD.Webtoon.NaverWebtoon.domain.Webtoon;
import com.example.AOD.Webtoon.NaverWebtoon.domain.WebtoonAuthor;
import com.example.AOD.Webtoon.NaverWebtoon.domain.WebtoonGenre;
import com.example.AOD.Webtoon.NaverWebtoon.domain.dto.NaverWebtoonDTO;
import com.example.AOD.Webtoon.NaverWebtoon.repository.WebtoonAuthorRepository;
import com.example.AOD.Webtoon.NaverWebtoon.repository.WebtoonGenreRepository;
import com.example.AOD.Webtoon.NaverWebtoon.repository.WebtoonRepository;
import com.example.AOD.util.ChromeDriverProvider;
import com.example.AOD.Webtoon.NaverWebtoon.util.NaverLoginHandler;

import java.awt.AWTException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import org.openqa.selenium.WebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WebtoonService {
    private static final Logger logger = LoggerFactory.getLogger(WebtoonService.class);

    private final WebtoonRepository webtoonRepository;
    private final NaverWebtoonCrawler naverWebtoonCrawler;
    private final WebtoonGenreRepository webtoonGenreRepository;
    private final WebtoonAuthorRepository webtoonAuthorRepository;

    /**
     * 각 웹툰을 저장하는 메소드
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public Webtoon saveWebtoon(NaverWebtoonDTO dto) {
        try {
            // null 체크: thumbnail이 null인 경우 기본 값 설정
            if (dto.getThumbnail() == null || dto.getThumbnail().isEmpty()) {
                logger.warn("웹툰 '{}' 썸네일이 null이어서 기본 이미지로 대체합니다.", dto.getTitle());
                dto.setThumbnail("https://ssl.pstatic.net/static/comic/images/og_tag_v2.png"); // 네이버 웹툰 기본 이미지
            }

            // URL로 기존 웹툰 확인 - 중복 저장 방지
            Webtoon existingWebtoon = null;
            try {
                existingWebtoon = webtoonRepository.findByUrl(dto.getUrl()).orElse(null);
            } catch (Exception e) {
                logger.error("웹툰 조회 중 오류 발생: {}", e.getMessage());
            }

            if (existingWebtoon != null) {
                logger.info("이미 존재하는 웹툰: {}", dto.getTitle());
                return existingWebtoon;
            }

            // 1. 먼저 웹툰 엔티티를 저장
            Webtoon webtoon = new Webtoon();
            webtoon.setTitle(dto.getTitle() != null ? dto.getTitle() : "제목 없음");
            webtoon.setUrl(dto.getUrl());
            webtoon.setPublishDate(dto.getPublishDate() != null ? dto.getPublishDate() : "날짜 정보 없음");
            webtoon.setSummary(dto.getSummary() != null ? dto.getSummary() : "줄거리 정보가 없습니다.");
            webtoon.setThumbnail(dto.getThumbnail()); // null 체크 완료

            // 업로드 날짜 null 체크
            if (dto.getUploadDays() != null) {
                webtoon.setUploadDays(dto.getUploadDays());
            } else {
                webtoon.setUploadDays(new ArrayList<>());
            }

            // 일단 빈 컬렉션으로 설정
            webtoon.setWebtoonAuthors(new ArrayList<>());
            webtoon.setWebtoonGenres(new ArrayList<>());

            // 먼저 웹툰 엔티티를 저장 (ManyToMany 관계 설정 전)
            try {
                webtoon = webtoonRepository.save(webtoon);
                logger.info("웹툰 기본 정보 저장 성공: {} (ID: {})", dto.getTitle(), webtoon.getId());
            } catch (Exception e) {
                logger.error("웹툰 기본 정보 저장 실패: {} - {}", dto.getTitle(), e.getMessage());
                throw e;
            }

            // 2. 저자 처리
            List<WebtoonAuthor> authors = new ArrayList<>();
            if (dto.getAuthors() != null) {
                for (String authorName : dto.getAuthors()) {
                    if (authorName == null || authorName.trim().isEmpty()) {
                        continue; // 빈 작가명 스킵
                    }

                    try {
                        WebtoonAuthor author = webtoonAuthorRepository.findByName(authorName)
                                .orElseGet(() -> {
                                    WebtoonAuthor newAuthor = new WebtoonAuthor(authorName);
                                    return webtoonAuthorRepository.save(newAuthor);
                                });

                        authors.add(author);
                    } catch (Exception e) {
                        logger.error("작가 처리 중 오류: {} - {}", authorName, e.getMessage());
                        // 계속 진행
                    }
                }
            }

            // 3. 장르 처리
            List<WebtoonGenre> genres = new ArrayList<>();
            if (dto.getGenres() != null) {
                for (String genreName : dto.getGenres()) {
                    if (genreName == null || genreName.trim().isEmpty()) {
                        continue; // 빈 장르명 스킵
                    }

                    try {
                        WebtoonGenre genre = webtoonGenreRepository.findByGenre(genreName)
                                .orElseGet(() -> {
                                    WebtoonGenre newGenre = new WebtoonGenre(genreName);
                                    return webtoonGenreRepository.save(newGenre);
                                });

                        genres.add(genre);
                    } catch (Exception e) {
                        logger.error("장르 처리 중 오류: {} - {}", genreName, e.getMessage());
                        // 계속 진행
                    }
                }
            }

            // 이미 저장된 웹툰에 작가와 장르 리스트 설정
            webtoon.setWebtoonAuthors(authors);
            webtoon.setWebtoonGenres(genres);

            // 웹툰 정보 업데이트
            try {
                webtoon = webtoonRepository.save(webtoon);
                logger.info("웹툰 정보 업데이트 성공: {} (ID: {})", dto.getTitle(), webtoon.getId());
                return webtoon;
            } catch (Exception e) {
                logger.error("웹툰 정보 업데이트 실패: {} - {}", dto.getTitle(), e.getMessage());
                e.printStackTrace();
                throw e;
            }
        } catch (Exception e) {
            logger.error("웹툰 저장 과정에서 예상치 못한 오류 발생: {}", e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    @Async
    public void crawl() throws InterruptedException, AWTException {
        ChromeDriverProvider chromeDriverProvider = new ChromeDriverProvider();
        NaverLoginHandler loginHandler = new NaverLoginHandler();
        WebDriver driver = chromeDriverProvider.getDriver();

        try {
            // 수동 로그인을 위한 코드
            logger.info("수동 로그인을 위해 로그인 페이지로 이동합니다. (30초 대기)");
            loginHandler.naverLogin(driver, "", "");

            logger.info("웹툰 크롤링 시작 (최대 25개)");
            ArrayList<NaverWebtoonDTO> naverWebtoonDTOS = naverWebtoonCrawler.crawlAllOngoingWebtoons(driver);
            logger.info("총 {} 개 웹툰 크롤링 완료", naverWebtoonDTOS.size());

            logger.info("웹툰 저장 시작");
            int successCount = 0;
            int failCount = 0;

            for (NaverWebtoonDTO naverWebtoonDTO : naverWebtoonDTOS) {
                if (naverWebtoonDTO != null) {
                    try {
                        // null 체크 추가
                        validateWebtoonDTO(naverWebtoonDTO);

                        // 각 웹툰을 별도의 트랜잭션으로 저장
                        saveWebtoon(naverWebtoonDTO);
                        successCount++;
                    } catch (Exception e) {
                        logger.error("웹툰 저장 중 오류 발생: {} - {}",
                                naverWebtoonDTO != null ? naverWebtoonDTO.getTitle() : "알 수 없는 웹툰",
                                e.getMessage());
                        failCount++;
                        // 계속 진행
                    }
                }
            }
            logger.info("웹툰 저장 완료: {}개 성공, {}개 실패", successCount, failCount);
        } catch (Exception e) {
            logger.error("크롤링 프로세스 중 오류 발생: {}", e.getMessage());
            e.printStackTrace();
        } finally {
            if (driver != null) {
                driver.quit();
                logger.info("WebDriver 종료");
            }
        }
    }

    @Async
    @Scheduled(cron = "0 0 0 * * ?") // 매일 자정에 실행
    public void crawlNewWebtoons() throws InterruptedException, AWTException {
        ChromeDriverProvider chromeDriverProvider = new ChromeDriverProvider();
        WebDriver driver = chromeDriverProvider.getDriver();
        NaverLoginHandler loginHandler = new NaverLoginHandler();

        try {
            // 수동 로그인을 위한 코드
            logger.info("수동 로그인을 위해 로그인 페이지로 이동합니다. (30초 대기)");
            loginHandler.naverLogin(driver, "", "");

            logger.info("신작 웹툰 크롤링 시작 (최대 25개)");
            // 신작 웹툰 크롤링 실행
            ArrayList<NaverWebtoonDTO> newWebtoons = naverWebtoonCrawler.crawlNewWebtoons(driver);
            logger.info("신작 웹툰 크롤링 완료: {}개 발견", newWebtoons.size());

            // 이미 저장된 웹툰은 제외하고 새로운 웹툰만 저장
            int savedCount = 0;
            int failCount = 0;

            for (NaverWebtoonDTO webtoonDTO : newWebtoons) {
                if (webtoonDTO != null) {
                    try {
                        // null 체크 추가
                        validateWebtoonDTO(webtoonDTO);

                        // 각 웹툰을 별도의 트랜잭션으로 저장
                        saveWebtoon(webtoonDTO);
                        savedCount++;
                    } catch (Exception e) {
                        logger.error("신작 웹툰 저장 중 오류 발생: {} - {}",
                                webtoonDTO != null ? webtoonDTO.getTitle() : "알 수 없는 웹툰",
                                e.getMessage());
                        failCount++;
                        // 계속 진행
                    }
                }
            }

            logger.info("신작 웹툰 저장 완료: {}개 신규 저장, {}개 실패", savedCount, failCount);
        } catch (Exception e) {
            logger.error("신작 웹툰 크롤링 프로세스 중 오류 발생: {}", e.getMessage());
            e.printStackTrace();
        } finally {
            if (driver != null) {
                driver.quit();
                logger.info("WebDriver 종료");
            }
        }
    }

    /**
     * NaverWebtoonDTO의 필수 필드가 null이 아닌지 확인하고 null인 경우 기본값 설정
     */
    private void validateWebtoonDTO(NaverWebtoonDTO dto) {
        if (dto == null) {
            throw new IllegalArgumentException("웹툰 DTO가 null입니다.");
        }

        // 필수 필드 검증 및 기본값 설정
        if (dto.getTitle() == null || dto.getTitle().isEmpty()) {
            logger.warn("웹툰 제목이 null이어서 기본값으로 대체합니다.");
            dto.setTitle("제목 없음");
        }

        if (dto.getUrl() == null || dto.getUrl().isEmpty()) {
            logger.error("웹툰 URL이 null입니다. 저장할 수 없습니다.");
            throw new IllegalArgumentException("웹툰 URL이 null입니다.");
        }

        if (dto.getThumbnail() == null || dto.getThumbnail().isEmpty()) {
            logger.warn("웹툰 썸네일이 null이어서 기본 이미지로 대체합니다.");
            dto.setThumbnail("https://ssl.pstatic.net/static/comic/images/og_tag_v2.png");
        }

        if (dto.getPublishDate() == null || dto.getPublishDate().isEmpty()) {
            logger.warn("웹툰 발행일이 null이어서 기본값으로 대체합니다.");
            dto.setPublishDate("정보 없음");
        }

        if (dto.getSummary() == null || dto.getSummary().isEmpty()) {
            logger.warn("웹툰 줄거리가 null이어서 기본값으로 대체합니다.");
            dto.setSummary("줄거리 정보가 없습니다.");
        }

        if (dto.getUploadDays() == null) {
            logger.warn("웹툰 업로드 요일이 null이어서 빈 리스트로 대체합니다.");
            dto.setUploadDays(new ArrayList<>());
        }

        if (dto.getAuthors() == null) {
            logger.warn("웹툰 작가 정보가 null이어서 빈 리스트로 대체합니다.");
            dto.setAuthors(new ArrayList<>());
        }

        if (dto.getGenres() == null) {
            logger.warn("웹툰 장르 정보가 null이어서 빈 리스트로 대체합니다.");
            dto.setGenres(new ArrayList<>());
        }
    }

    /**
     * 단일 웹툰을 크롤링하고 저장합니다.
     * @param titleId 웹툰 ID
     * @return 성공 여부
     */
    @Transactional
    public boolean crawlSingleWebtoon(String titleId) {
        ChromeDriverProvider chromeDriverProvider = new ChromeDriverProvider();
        WebDriver driver = chromeDriverProvider.getDriver();
        NaverWebtoonCrawler crawler = new NaverWebtoonCrawler();

        try {
            String webtoonUrl = "https://comic.naver.com/webtoon/list?titleId=" + titleId;
            logger.info("단일 웹툰 크롤링 시작: {}", webtoonUrl);

            NaverWebtoonDTO webtoonDTO = crawler.crawlWebtoonDetails(webtoonUrl, driver);
            if (webtoonDTO != null) {
                // null 체크 추가
                validateWebtoonDTO(webtoonDTO);

                saveWebtoon(webtoonDTO);
                logger.info("단일 웹툰 저장 성공: {}", webtoonDTO.getTitle());
                return true;
            } else {
                logger.error("웹툰 정보를 가져오지 못했습니다. titleId: {}", titleId);
                return false;
            }
        } catch (Exception e) {
            logger.error("단일 웹툰 크롤링 및 저장 중 오류 발생: {}", e.getMessage());
            e.printStackTrace();
            return false;
        } finally {
            if (driver != null) {
                driver.quit();
                logger.info("WebDriver 종료");
            }
        }
    }
}