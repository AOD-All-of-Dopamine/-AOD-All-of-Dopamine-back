package com.example.AOD.Webtoon.NaverWebtoon;


import com.example.AOD.ingest.CollectorService;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
     * 모든 요일별 웹툰 크롤링
     */
    public int crawlAllWeekdays() throws Exception {
        int totalSaved = 0;

        for (String weekday : WEEKDAYS) {
            log.info("크롤링 시작: {} 요일", weekday);
            int saved = crawlWeekday(weekday);
            totalSaved += saved;
            log.info("{} 요일 크롤링 완료: {}개 저장", weekday, saved);
        }

        return totalSaved;
    }

    /**
     * 특정 요일 웹툰 크롤링
     */
    public int crawlWeekday(String weekday) throws Exception {
        String url = BASE_WEEKDAY_URL + weekday;
        String crawlSource = "weekday_" + weekday;
        return crawlWebtoonList(url, crawlSource, weekday, 0); // maxPages=0 (무제한)
    }

    /**
     * 완결 웹툰 크롤링 (페이지네이션)
     */
    public int crawlFinishedWebtoons(int maxPages) throws Exception {
        String crawlSource = "finish";
        return crawlWebtoonListWithPagination(BASE_FINISH_URL, crawlSource, null, maxPages);
    }

    /**
     * 웹툰 목록 크롤링 (페이지네이션 지원)
     */
    private int crawlWebtoonListWithPagination(String baseUrl, String crawlSource, String weekday, int maxPages) throws Exception {
        int totalSaved = 0;
        int page = 1;

        while (true) {
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
                        saveToRaw(completeDTO);
                        totalSaved++;

                        // 과도한 요청 방지를 위한 딜레이
                        Thread.sleep(NaverWebtoonSelectors.PAGE_DELAY);

                    } catch (Exception e) {
                        log.warn("웹툰 상세 정보 보완 실패: {}, {}", mobileUrl, e.getMessage());

                        // 상세 정보 보완 실패해도 기본 정보라도 저장
                        try {
                            saveToRaw(basicDTO);
                            totalSaved++;
                        } catch (Exception e2) {
                            log.error("기본 정보 저장도 실패: {}, {}", mobileUrl, e2.getMessage());
                        }
                    }
                }

                page++;

                // 페이지 간 딜레이
                Thread.sleep(NaverWebtoonSelectors.PAGE_DELAY);

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
                saveToRaw(completeDTO);
                saved++;

                // 과도한 요청 방지를 위한 딜레이
                Thread.sleep(NaverWebtoonSelectors.PAGE_DELAY);

            } catch (Exception e) {
                log.warn("웹툰 상세 정보 보완 실패: {}, {}", mobileUrl, e.getMessage());

                // 상세 정보 보완 실패해도 기본 정보라도 저장
                try {
                    saveToRaw(basicDTO);
                    saved++;
                } catch (Exception e2) {
                    log.error("기본 정보 저장도 실패: {}, {}", mobileUrl, e2.getMessage());
                }
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

            // PC 파싱 실패시 기본 정보라도 반환
            log.warn("PC 페이지 파싱 실패, 목록 기본 정보만 사용: {}", pcUrl);
            return basicDTO;

        } catch (Exception e) {
            log.warn("PC 페이지 접근 실패, 목록 기본 정보만 사용: {}, 오류: {}", pcUrl, e.getMessage());
            return basicDTO;
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
                .isFree(basicDTO.getIsFree() != null ? basicDTO.getIsFree() : detailedDTO.getIsFree())
                .hasAdult(basicDTO.getHasAdult() != null ? basicDTO.getHasAdult() : detailedDTO.getHasAdult())
                .serviceType(basicDTO.getServiceType() != null ? basicDTO.getServiceType() : detailedDTO.getServiceType())
                .originalPlatform(basicDTO.getOriginalPlatform())
                .crawlSource(basicDTO.getCrawlSource())

                // PC에서만 수집 가능한 상세 정보
                .synopsis(detailedDTO.getSynopsis())
                .productUrl(detailedDTO.getProductUrl()) // PC URL 사용
                .episodeCount(detailedDTO.getEpisodeCount())
                .startedAt(detailedDTO.getStartedAt())
                .endedAt(detailedDTO.getEndedAt())
                .ageRating(detailedDTO.getAgeRating())
                .publisher(detailedDTO.getPublisher())
                .genres(detailedDTO.getGenres())
                .tags(detailedDTO.getTags())
                .rating(detailedDTO.getRating())
                .viewCount(detailedDTO.getViewCount())
                .commentCount(detailedDTO.getCommentCount())
                .subscriberCount(detailedDTO.getSubscriberCount())
                .latestEpisodeTitle(detailedDTO.getLatestEpisodeTitle())
                .latestEpisodeUrl(detailedDTO.getLatestEpisodeUrl())
                .latestEpisodeDate(detailedDTO.getLatestEpisodeDate())
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
        payload.put("startedAt", dto.getStartedAt());
        payload.put("endedAt", dto.getEndedAt());

        payload.put("ageRating", nz(dto.getAgeRating()));
        payload.put("publisher", nz(dto.getPublisher()));
        payload.put("genres", dto.getGenres());
        payload.put("tags", dto.getTags());

        payload.put("rating", dto.getRating());
        payload.put("viewCount", dto.getViewCount());
        payload.put("likeCount", dto.getLikeCount());
        payload.put("commentCount", dto.getCommentCount());
        payload.put("subscriberCount", dto.getSubscriberCount());

        payload.put("latestEpisodeTitle", nz(dto.getLatestEpisodeTitle()));
        payload.put("latestEpisodeUrl", nz(dto.getLatestEpisodeUrl()));
        payload.put("latestEpisodeDate", dto.getLatestEpisodeDate());

        payload.put("isFree", dto.getIsFree());
        payload.put("hasAdult", dto.getHasAdult());
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
