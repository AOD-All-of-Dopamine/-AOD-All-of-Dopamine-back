package com.example.crawler.contents.Webtoon.NaverWebtoon;


import com.example.crawler.ingest.CollectorService;
import com.example.crawler.util.InterruptibleSleep;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 네이버 웹툰 모바일 크롤러
 * - 모바일 페이지 사용 (동적 로드 없음, 페이지네이션 지원)
 * - 요일별, 완결작 크롤링
 * - raw_items에 평평한 구조로 저장
 */
@Component
@Slf4j
public class NaverWebtoonCrawler {

    private final CollectorService collector;
    private final WebtoonPageParser pageParser;
    private final MobileListParser mobileListParser;

    // URL 상수들
    private static final String BASE_WEEKDAY_URL = "https://m.comic.naver.com/webtoon/weekday?week=";
    private static final String BASE_FINISH_URL = "https://m.comic.naver.com/webtoon/finish";
    private static final String[] WEEKDAYS = {"mon", "tue", "wed", "thu", "fri", "sat", "sun"};

    public NaverWebtoonCrawler(CollectorService collector, WebtoonPageParser pageParser, MobileListParser mobileListParser) {
        this.collector = collector;
        this.pageParser = pageParser;
        this.mobileListParser = mobileListParser;
    }

    /**
     * PageParser 접근자 (Service 레이어에서 cleanup 위해 필요)
     */
    public WebtoonPageParser getPageParser() {
        return pageParser;
    }

    /**
     * 모든 요일별 웹툰 크롤링
     */
    public int crawlAllWeekdays() throws Exception {
        int totalSaved = 0;

        try {
            for (String weekday : WEEKDAYS) {
                log.info("크롤링 시작: {} 요일", weekday);
                int saved = crawlWeekday(weekday);
                totalSaved += saved;
                log.info("{} 요일 크롤링 완료: {}개 저장", weekday, saved);
            }
        } finally {
            // WebDriver 자원 정리
            cleanupParser();
        }

        return totalSaved;
    }

    /**
     * 특정 요일 웹툰 크롤링
     */
    public int crawlWeekday(String weekday) throws Exception {
        String url = BASE_WEEKDAY_URL + weekday;
        String crawlSource = "weekday_" + weekday;
        try {
            return crawlWebtoonList(url, crawlSource, weekday, 0); // maxPages=0 (무제한)
        } finally {
            // 개별 크롤링 후 정리
            cleanupParser();
        }
    }

    /**
     * 완결 웹툰 크롤링 (페이지네이션)
     * 완결 웹툰은 weekday를 null로 설정
     */
    public int crawlFinishedWebtoons(int maxPages) throws Exception {
        String crawlSource = "finish";
        try {
            // 완결 웹툰은 weekday를 null로 전달
            return crawlWebtoonListWithPagination(BASE_FINISH_URL, crawlSource, null, maxPages);
        } finally {
            // 크롤링 후 정리
            cleanupParser();
        }
    }
    
    /**
     * WebDriver 자원 정리 (Selenium 파서인 경우)
     */
    private void cleanupParser() {
        if (pageParser instanceof NaverWebtoonSeleniumPageParser) {
            ((NaverWebtoonSeleniumPageParser) pageParser).cleanup();
        }
    }

    /**
     * 웹툰 목록 크롤링 (페이지네이션 지원)
     */
    private int crawlWebtoonListWithPagination(String baseUrl, String crawlSource, String weekday, int maxPages) throws Exception {
        int totalSaved = 0;
        int page = 1;

        while (true) {
            // 인터럽트 체크 - 작업 취소 요청 확인
            if (Thread.currentThread().isInterrupted()) {
                log.info("작업 인터럽트 감지, 크롤링 중단 (현재까지 {}개 저장)", totalSaved);
                return totalSaved;
            }
            
            if (maxPages > 0 && page > maxPages) break;

            String pageUrl = baseUrl + (baseUrl.contains("?") ? "&page=" : "?page=") + page;

            try {
                Document listDoc = get(pageUrl);

                // 목록에서 웹툰과 기본 정보를 함께 추출
                Map<String, NaverWebtoonDTO> webtoonsWithBasicInfo = extractWebtoonsWithBasicInfo(listDoc, crawlSource, weekday);

                if (webtoonsWithBasicInfo.isEmpty()) {
                    log.info("페이지 {}에서 더 이상 웹툰이 없음, 크롤링 종료", page);
                    break;
                }

                log.debug("페이지 {}: {}개 웹툰 발견", page, webtoonsWithBasicInfo.size());

                // 각 웹툰의 상세 정보 보완 및 저장
                for (Map.Entry<String, NaverWebtoonDTO> entry : webtoonsWithBasicInfo.entrySet()) {
                    String mobileUrl = entry.getKey();
                    NaverWebtoonDTO basicDTO = entry.getValue();

                    try {
                        // PC 페이지에서 상세 정보 보완
                        NaverWebtoonDTO completeDTO = enrichWithPcDetails(basicDTO, mobileUrl);
                        
                        // 19금 작품 등으로 제목을 찾을 수 없는 경우 스킵
                        if (completeDTO == null || completeDTO.getTitle() == null || completeDTO.getTitle().trim().isEmpty()) {
                            log.info("제목을 찾을 수 없는 작품 스킵 (19금 등): {}", mobileUrl);
                            continue;
                        }
                        
                        saveToRaw(completeDTO);
                        totalSaved++;

                        // 과도한 요청 방지를 위한 딜레이
                        if (!InterruptibleSleep.sleep(NaverWebtoonSelectors.PAGE_DELAY)) {
                            log.info("크롤링 인터럽트 발생, 작업 중단");
                            return totalSaved; // 인터럽트 시 즉시 종료
                        }

                    } catch (InterruptedException e) {
                        log.info("웹툰 크롤링 인터럽트 발생, 작업 중단");
                        Thread.currentThread().interrupt();
                        return totalSaved;
                    } catch (Exception e) {
                        log.warn("웹툰 크롤링 실패, 스킵: {}, {}", mobileUrl, e.getMessage());
                    }
                }

                page++;

                // 페이지 간 딜레이
                if (!InterruptibleSleep.sleep(NaverWebtoonSelectors.PAGE_DELAY)) {
                    log.info("페이지 간 대기 중 인터럽트 발생, 작업 중단");
                    return totalSaved;
                }

            } catch (Exception e) {
                log.error("페이지 {} 크롤링 실패: {}", page, e.getMessage());
                break;
            }
        }

        return totalSaved;
    }

    /**
     * 단일 페이지 웹툰 목록 크롤링 (요일별용)
     */
    private int crawlWebtoonList(String url, String crawlSource, String weekday, int maxPages) throws Exception {
        Document listDoc = get(url);

        // 목록에서 웹툰과 기본 정보를 함께 추출
        Map<String, NaverWebtoonDTO> webtoonsWithBasicInfo = extractWebtoonsWithBasicInfo(listDoc, crawlSource, weekday);

        if (webtoonsWithBasicInfo.isEmpty()) {
            log.warn("웹툰 목록이 비어있음: {}", url);
            return 0;
        }

        log.debug("{}개 웹툰 발견", webtoonsWithBasicInfo.size());

        int saved = 0;
        for (Map.Entry<String, NaverWebtoonDTO> entry : webtoonsWithBasicInfo.entrySet()) {
            String mobileUrl = entry.getKey();
            NaverWebtoonDTO basicDTO = entry.getValue();

            try {
                // PC 페이지에서 상세 정보 보완
                NaverWebtoonDTO completeDTO = enrichWithPcDetails(basicDTO, mobileUrl);
                
                // 19금 작품 등으로 제목을 찾을 수 없는 경우 스킵
                if (completeDTO == null || completeDTO.getTitle() == null || completeDTO.getTitle().trim().isEmpty()) {
                    log.info("제목을 찾을 수 없는 작품 스킵 (19금 등): {}", mobileUrl);
                    continue;
                }
                
                saveToRaw(completeDTO);
                saved++;

                // 과도한 요청 방지를 위한 딜레이
                if (!InterruptibleSleep.sleep(NaverWebtoonSelectors.PAGE_DELAY)) {
                    log.info("요일별 크롤링 인터럽트 발생, 작업 중단");
                    return saved;
                }

            } catch (InterruptedException e) {
                log.info("요일별 웹툰 크롤링 인터럽트 발생, 작업 중단");
                Thread.currentThread().interrupt();
                return saved;
            } catch (Exception e) {
                log.warn("웹툰 크롤링 실패, 스킵: {}, {}", mobileUrl, e.getMessage());
            }
        }

        return saved;
    }

    /**
     * 모바일 목록에서 웹툰과 기본 정보를 함께 추출
     */
    private Map<String, NaverWebtoonDTO> extractWebtoonsWithBasicInfo(Document listDoc, String crawlSource, String weekday) {
        return mobileListParser.extractWebtoonsWithBasicInfo(listDoc, crawlSource, weekday);
    }

    /**
     * PC 웹툰 상세 페이지에서 추가 정보를 보완하여 완전한 DTO 생성
     *
     * @param basicDTO 목록에서 추출한 기본 정보
     * @param mobileUrl 모바일 URL
     * @return 완전한 웹툰 정보가 담긴 DTO
     */
    private NaverWebtoonDTO enrichWithPcDetails(NaverWebtoonDTO basicDTO, String mobileUrl) throws Exception {
        // 모바일 URL을 PC URL로 변환
        String pcUrl = pageParser.convertToPcUrl(mobileUrl);

        log.debug("URL 변환: {} -> {}", mobileUrl, pcUrl);

        try {
            Document pcDoc = get(pcUrl);

            // PC 페이지에서 추가 정보 파싱하여 기본 DTO에 보완
            NaverWebtoonDTO enrichedDTO = pageParser.parseWebtoonDetail(pcDoc, pcUrl, basicDTO.getCrawlSource(), basicDTO.getWeekday());

            if (enrichedDTO != null) {
                // 목록에서 수집한 기본 정보를 우선 사용하고, PC에서 수집한 정보로 보완
                return mergeBasicAndDetailedInfo(basicDTO, enrichedDTO);
            }

            // PC 파싱 실패시 null 반환 (19금 작품 등)
            log.warn("PC 페이지 파싱 실패, 작품 스킵: {}", pcUrl);
            return null;

        } catch (Exception e) {
            log.warn("PC 페이지 접근 실패, 작품 스킵: {}, 오류: {}", pcUrl, e.getMessage());
            return null;
        }
    }

    /**
     * 목록 기본 정보와 PC 상세 정보를 결합
     */
    private NaverWebtoonDTO mergeBasicAndDetailedInfo(NaverWebtoonDTO basicDTO, NaverWebtoonDTO detailedDTO) {
        return NaverWebtoonDTO.builder()
                // 목록에서 수집한 정보 우선 사용
                .title(basicDTO.getTitle())
                .author(basicDTO.getAuthor() != null ? basicDTO.getAuthor() : detailedDTO.getAuthor())
                .imageUrl(basicDTO.getImageUrl() != null ? basicDTO.getImageUrl() : detailedDTO.getImageUrl())
                .titleId(basicDTO.getTitleId())
                .weekday(basicDTO.getWeekday())
                .status(basicDTO.getStatus() != null ? basicDTO.getStatus() : detailedDTO.getStatus())
                .likeCount(basicDTO.getLikeCount() != null ? basicDTO.getLikeCount() : detailedDTO.getLikeCount())
                .serviceType(basicDTO.getServiceType() != null ? basicDTO.getServiceType() : detailedDTO.getServiceType())
                .originalPlatform(basicDTO.getOriginalPlatform())
                .crawlSource(basicDTO.getCrawlSource())

                // PC에서만 수집 가능한 상세 정보
                .episodeCount(detailedDTO.getEpisodeCount())
                .likeCount(detailedDTO.getLikeCount())
                .synopsis(detailedDTO.getSynopsis())
                .productUrl(detailedDTO.getProductUrl()) // PC URL 사용
                .ageRating(detailedDTO.getAgeRating())
                .tags(detailedDTO.getTags())
                .releaseDate(detailedDTO.getReleaseDate()) // 첫 화 연재 날짜 (PC에서만 수집)
                .build();
    }

    /**
     * DTO를 raw_items에 저장
     */
    private void saveToRaw(NaverWebtoonDTO dto) {
        Map<String, Object> payload = new LinkedHashMap<>();

        // 모든 DTO 필드를 평평한 Map으로 변환
        payload.put("title", nz(dto.getTitle()));
        payload.put("author", nz(dto.getAuthor()));
        payload.put("synopsis", nz(dto.getSynopsis()));
        payload.put("imageUrl", nz(dto.getImageUrl()));
        payload.put("productUrl", nz(dto.getProductUrl()));

        payload.put("titleId", nz(dto.getTitleId()));
        payload.put("weekday", nz(dto.getWeekday()));
        payload.put("status", nz(dto.getStatus()));
        payload.put("episodeCount", dto.getEpisodeCount());
        // LocalDate를 String으로 변환하여 저장 (JSON 직렬화 문제 방지)
        payload.put("releaseDate", dto.getReleaseDate() != null ? dto.getReleaseDate().toString() : null);

        payload.put("ageRating", nz(dto.getAgeRating()));
        payload.put("tags", dto.getTags());

        payload.put("likeCount", dto.getLikeCount());

        payload.put("serviceType", nz(dto.getServiceType()));

        payload.put("originalPlatform", nz(dto.getOriginalPlatform()));
        payload.put("crawlSource", nz(dto.getCrawlSource()));

        // CollectorService를 통해 raw_items에 저장
        collector.saveRaw("NaverWebtoon", "WEBTOON", payload, dto.getTitleId(), dto.getProductUrl());
    }

    // ==== 유틸리티 메서드들 ====

    private Document get(String url) throws Exception {
        // URL에 따라 적절한 User-Agent 선택
        String userAgent = url.contains(NaverWebtoonSelectors.MOBILE_DOMAIN)
                ? NaverWebtoonSelectors.MOBILE_USER_AGENT
                : NaverWebtoonSelectors.PC_USER_AGENT;

        return Jsoup.connect(url)
                .userAgent(userAgent)
                .timeout(NaverWebtoonSelectors.CONNECTION_TIMEOUT)
                .get();
    }

    private String nz(String str) {
        return str == null ? "" : str;
    }
}


