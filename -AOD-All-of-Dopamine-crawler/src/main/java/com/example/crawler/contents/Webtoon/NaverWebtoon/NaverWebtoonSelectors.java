package com.example.crawler.contents.Webtoon.NaverWebtoon;

/**
 * 네이버 웹툰 CSS 셀렉터 설정 클래스
 * - 목록: 모바일 페이지 (m.comic.naver.com)
 * - 상세: PC 페이지 (comic.naver.com)
 * - 나중에 실제 CSS 셀렉터로 교체할 부분들을 추상화
 */
public class NaverWebtoonSelectors {

    // ===== 목록 페이지 셀렉터 (모바일: m.comic.naver.com) =====

    // 웹툰 링크 셀렉터 (우선순위 순)
    public static final String[] WEBTOON_LINK_SELECTORS = {
            "ul.list_toon li.item a.link[href*='titleId=']",  // 메인 패턴
            "ul.list_finish li.item a.link[href*='titleId=']", // 완결작 패턴
            "ul.list_toon li.item a.link[href*='/webtoon/list']", // 폴백 패턴
            "a[href*='titleId=']"                             // 최종 폴백
    };

    // 웹툰 제목 (목록에서)
    public static final String LIST_WEBTOON_TITLE = "div.info div.title_box strong.title span.title_text";

    // 웹툰 작가 (목록에서)
    public static final String LIST_WEBTOON_AUTHOR = "div.info span.author";

    // 웹툰 썸네일 (목록에서)
    public static final String LIST_WEBTOON_THUMBNAIL = "div.thumbnail img";

    // 관심수 (목록에서)
    public static final String LIST_WEBTOON_LIKE_COUNT = "div.info span.favcount span.count_num";

    // 뱃지들 (유료작품, 신작, 청소년이용불가 등)
    public static final String LIST_WEBTOON_BADGES = "div.thumbnail span.area_badge span.badge";

    // 상태 표시 (업데이트, 휴재 등)
    public static final String LIST_WEBTOON_STATUS = "div.info div.title_box span.bullet";

    // ===== 상세 페이지 셀렉터 (PC: comic.naver.com) =====

    // 기본 메타 정보
    public static final String DETAIL_TITLE = "h2.EpisodeListInfo__title--mYLjC";
    public static final String DETAIL_AUTHORS = "div.ContentMetaInfo__meta_info--GbTg4 a.ContentMetaInfo__link--xTtO6";
    public static final String DETAIL_SYNOPSIS = "p.EpisodeListInfo__summary--Jd1WG";
    public static final String DETAIL_THUMBNAIL = "img.Poster__image--d9XTI";

    // 연재 정보 (금요웹툰 ∙ 15세 이용가)
    public static final String DETAIL_META_INFO = "em.ContentMetaInfo__info_item--utGrf";

    // 태그/장르 정보
    public static final String DETAIL_TAGS = "div.TagGroup__tag_group--uUJza a.TagGroup__tag--xu0OH";

    // 작가별 구분을 위한 추가 셀렉터들
    public static final String DETAIL_AUTHOR_WRITER = "div.ContentMetaInfo__meta_info--GbTg4 span.ContentMetaInfo__category--WwrCp:contains('글') a.ContentMetaInfo__link--xTtO6";
    public static final String DETAIL_AUTHOR_ARTIST = "div.ContentMetaInfo__meta_info--GbTg4 span.ContentMetaInfo__category--WwrCp:contains('그림') a.ContentMetaInfo__link--xTtO6";
    public static final String DETAIL_AUTHOR_ORIGINAL = "div.ContentMetaInfo__meta_info--GbTg4 span.ContentMetaInfo__category--WwrCp:contains('원작') a.ContentMetaInfo__link--xTtO6";

    public static final String DETAIL_LIKE_COUNT = "span.EpisodeListUser__count--fNEWK";  // PC 상세페이지 관심수



    // ===== 메타 태그 (폴백용) =====

    public static final String META_OG_TITLE = "meta[property=og:title]";
    public static final String META_OG_DESCRIPTION = "meta[property=og:description]";
    public static final String META_OG_IMAGE = "meta[property=og:image]";
    public static final String META_OG_URL = "meta[property=og:url]";

    // ===== URL 패턴 =====

    public static final String TITLE_ID_PATTERN = "titleId=([^&]+)";
    public static final String WEBTOON_ID_PATTERN = "/webtoon/detail\\?titleId=([^&]+)";

    // ===== 기타 설정 =====

    // User-Agent - 모바일용
    public static final String MOBILE_USER_AGENT = "Mozilla/5.0 (iPhone; CPU iPhone OS 14_7_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/14.1.2 Mobile/15E148 Safari/604.1";

    // User-Agent - PC용
    public static final String PC_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36";

    // 타임아웃
    public static final int CONNECTION_TIMEOUT = 30000;

    // 페이지 대기 시간 (밀리초)
    public static final int PAGE_DELAY = 1000;

    // URL 변환 관련
    public static final String MOBILE_DOMAIN = "m.comic.naver.com";
    public static final String PC_DOMAIN = "comic.naver.com";
}

