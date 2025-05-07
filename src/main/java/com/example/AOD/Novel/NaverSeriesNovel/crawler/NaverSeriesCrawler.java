package com.example.AOD.Novel.NaverSeriesNovel.crawler;

import com.example.AOD.Novel.NaverSeriesNovel.dto.NaverSeriesNovelDTO;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.List;

public class NaverSeriesCrawler {
    /**
     * url을 기준으로 전체, 신작 소설 크롤링.
     */
    public List<NaverSeriesNovelDTO> crawlNovels(String baseListUrl, String cookieString) {
        List<NaverSeriesNovelDTO> novels = new ArrayList<>();
        // 쿠키 검증
        if (cookieString.isEmpty()) {
            System.out.println("쿠키 추출 실패 => 프로그램 종료.");
            return novels;
        }

        try {
            int page = 1;
            boolean hasMorePages = true;

            // 페이지에 작품이 없을 때까지 계속 크롤링
            while (hasMorePages) {
                String pageUrl = baseListUrl;
                // URL에 이미 파라미터가 있는지 확인하여 페이지 파라미터 추가
                if (pageUrl.contains("?")) {
                    pageUrl += "&page=" + page;
                } else {
                    pageUrl += "?page=" + page;
                }

                Document listDoc = getDocumentWithCookies(pageUrl, cookieString);
                Elements items = listDoc.select("ul.lst_list li");

                if (items.isEmpty()) {
                    System.out.println(page + "페이지 작품이 없음. 크롤링 종료.");
                    hasMorePages = false;
                    break;
                }

                System.out.println("[페이지 " + page + "] 작품 수: " + items.size());

                // 각 소설 상세 페이지 파싱
                for (Element li : items) {
                    Element linkElem = li.selectFirst("a.pic");
                    if (linkElem == null) continue;
                    String detailUrl = linkElem.attr("href");
                    if (!detailUrl.startsWith("http")) {
                        detailUrl = "https://series.naver.com" + detailUrl;
                    }

                    // 상세 페이지 접근 후 파싱하여 DTO 생성
                    Document detailDoc = getDocumentWithCookies(detailUrl, cookieString);
                    NaverSeriesNovelDTO dto = parseDetailPage(detailDoc, detailUrl);
                    if (dto != null) {
                        novels.add(dto);
                        System.out.println("소설 정보 추출 완료: " + dto.getTitle());
                    }

                    // 과도한 요청 방지 위한 딜레이
                    Thread.sleep(1000);
                }

                // 페이지 증가 및 페이지 간 딜레이
                page++;
                Thread.sleep(2000);
            }

            System.out.println("소설 크롤링 완료. 총 " + novels.size() + "개 소설 수집됨");
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("크롤링 중 오류 발생: " + e.getMessage());
        }

        return novels;
    }

    /**
     * 최근 웹소설 목록을 크롤링하여 DTO 리스트로 반환
     */
    public List<NaverSeriesNovelDTO> crawlRecentNovels(String cookieString) {
        String recentListUrl = "https://series.naver.com/novel/recentList.series";
        return crawlNovels(recentListUrl, cookieString);
    }

    /**
     * 전체 웹소설 목록을 크롤링하여 DTO 리스트로 반환
     */
    public List<NaverSeriesNovelDTO> crawlAllNovels(String cookieString) {
        String allListUrl = "https://series.naver.com/novel/categoryProductList.series?categoryTypeCode=all";
        return crawlNovels(allListUrl, cookieString);
    }
    /**
     * 최근 웹소설 목록을 크롤링하여 DTO 리스트로 반환
     */
/*    public List<NaverSeriesNovelDTO> crawlRecentNovels(String cookieString) {
        List<NaverSeriesNovelDTO> novels = new ArrayList<>();
        // 1. Selenium 등을 이용한 로그인 후 쿠키 획득
        if (cookieString.isEmpty()) {
            System.out.println("쿠키 추출 실패 => 프로그램 종료.");
            return novels;
        }

        try {
            // 예제: 1~3 페이지 크롤링
            for (int page = 1; page <= 3; page++) {
                String listUrl = "https://series.naver.com/novel/recentList.series?page=" + page;
                Document listDoc = getDocumentWithCookies(listUrl, cookieString);
                Elements items = listDoc.select("ul.lst_list li");
                if (items.isEmpty()) {
                    System.out.println(page + "페이지 작품이 없음.");
                    break;
                }

                for (Element li : items) {
                    Element linkElem = li.selectFirst("a.pic");
                    if (linkElem == null) continue;
                    String detailUrl = linkElem.attr("href");
                    if (!detailUrl.startsWith("http")) {
                        detailUrl = "https://series.naver.com" + detailUrl;
                    }

                    // 상세 페이지 접근 후 파싱하여 DTO 생성
                    Document detailDoc = getDocumentWithCookies(detailUrl, cookieString);
                    NaverSeriesNovelDTO dto = parseDetailPage(detailDoc, detailUrl);
                    if (dto != null) {
                        novels.add(dto);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return novels;
    }*/


    private NaverSeriesNovelDTO parseDetailPage(Document detailDoc, String detailUrl) {
        // 상세 페이지 URL 로그
        System.out.println("상세 페이지 파싱 시작: " + detailUrl);

        // 작품 제목 (og:title 메타 태그)
        String title = detailDoc.select("meta[property=og:title]").attr("content");
        System.out.println("작품 제목: " + title);

        // 기본 정보 변수 초기화
        String status = "";
        String genre = "";
        String writer = "";
        String publisher = "";
        String ageRating = "";

        // info_lst 정보를 포함하는 ul 요소
        Element infoUl = detailDoc.selectFirst("li.info_lst ul");
        if (infoUl != null) {
            Elements lis = infoUl.select("li");
            for (Element li : lis) {
                String text = li.text().trim();
                // 연재상태 추출
                if (text.contains("완결")) {
                    status = "완결";
                } else if (text.contains("연재중")) {
                    status = "연재중";
                }
                // 작가 정보 (예: "글")
                if (text.contains("글")) {
                    Element anchor = li.selectFirst("a");
                    if (anchor != null) {
                        writer = anchor.text().trim();
                    }
                }
                // 출판사 정보
                if (text.contains("출판사")) {
                    Element anchor = li.selectFirst("a");
                    if (anchor != null) {
                        publisher = anchor.text().trim();
                    }
                }
                // 연령가 (ex: "청소년 이용불가", "전체 이용가")
                if (text.contains("이용가") || text.contains("이용불가")) {
                    ageRating = text;
                }
                // 첫 번째로 발견되는 장르 정보
                if (genre.isEmpty()) {
                    Element anchor = li.selectFirst("a");
                    if (anchor != null && !text.contains("글") && !text.contains("출판사")) {
                        genre = anchor.text().trim();
                    }
                }
            }
        }
        System.out.println("연재상태: " + status);
        System.out.println("장르: " + genre);
        System.out.println("글작가: " + writer);
        System.out.println("출판사: " + publisher);
        System.out.println("연령가: " + ageRating);

        // 줄거리(시놉시스) 파싱 추가
        Elements synopsisDivs = detailDoc.select("div._synopsis");
        if (synopsisDivs.isEmpty()){
            System.out.println("줄거리 없음: _synopsis를 찾을 수 없음");
        } else {
            Element lastDiv = synopsisDivs.last();
            String synopsis = lastDiv.text().trim();
            System.out.println("줄거리(마지막 _synopsis): " + synopsis);
        }

        // DTO 생성 및 값 설정
        NaverSeriesNovelDTO dto = new NaverSeriesNovelDTO();
        dto.setTitle(title);
        dto.setUrl(detailUrl);
        dto.setStatus(status);
        dto.setPublisher(publisher);
        dto.setAgeRating(ageRating);
        dto.setAuthor(writer);

        List<String> genres = new ArrayList<>();
        if (!genre.isEmpty()) {
            genres.add(genre);
        }
        dto.setGenres(genres);

        return dto;
    }

    /**
     * 쿠키 헤더를 포함하여 인증된 상태로 Jsoup GET 요청
     */
    private Document getDocumentWithCookies(String url, String cookieString) throws Exception {
        return Jsoup.connect(url)
                .header("Cookie", cookieString)
                .userAgent("Mozilla/5.0")
                .timeout(10000)
                .get();
    }
}
