//package com.example.AOD.NaverSeriesNovel;
//
//import com.example.AOD.NaverWebtoonCrawler.util.NaverLoginHandler;
//import org.jsoup.Jsoup;
//import org.jsoup.nodes.Document;
//import org.jsoup.nodes.Element;
//import org.jsoup.select.Elements;
//
///**
// * Selenium 로그인을 통해 얻은 쿠키를 사용해서
// * Jsoup으로 네이버 시리즈를 정적 크롤링 (19금 포함) 예시
// */
//public class NaverSeriesJsoup {
//
//    public static void main(String[] args) {
//        // 1. Selenium으로 로그인 & 쿠키 추출
//        String cookieString = NaverLoginHandler.getCookieString();
//        if (cookieString.isEmpty()) {
//            System.out.println("쿠키 추출 실패 => 프로그램 종료.");
//            return;
//        }
//
//        try {
//            // 2. 예: 신작 리스트 1~3 페이지
//            for (int page = 1; page <= 3; page++) {
//                String listUrl = "https://series.naver.com/novel/recentList.series?page=" + page;
//                Document listDoc = getDocumentWithCookies(listUrl, cookieString);
//
//                Elements items = listDoc.select("ul.lst_list li");
//                if (items.isEmpty()) {
//                    System.out.println(page + "페이지 작품이 없음.");
//                    break;
//                }
//
//                System.out.println("[페이지 " + page + "] 작품 수: " + items.size());
//                // 각 아이템에서 상세 페이지 URL 추출
//                for (Element li : items) {
//                    Element linkElem = li.selectFirst("a.pic");
//                    if (linkElem == null) continue;
//                    String detailUrl = linkElem.attr("href");
//                    if (!detailUrl.startsWith("http")) {
//                        detailUrl = "https://series.naver.com" + detailUrl;
//                    }
//
//                    // 상세 페이지 접근
//                    Document detailDoc = getDocumentWithCookies(detailUrl, cookieString);
//                    parseDetailPage(detailDoc);
//                    System.out.println("---------------------------------");
//                }
//                System.out.println("=================================");
//            }
//
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//    }
//
//    /**
//     * 상세 페이지에서 info_lst(작품 정보) + _synopsis(줄거리) 파싱 예시
//     */
//    private static void parseDetailPage(Document detailDoc) {
//        // 1) 작품 제목 (og:title)
//        String title = detailDoc.select("meta[property=og:title]").attr("content");
//        System.out.println("작품 제목: " + title);
//
//        // 2) info_lst 예: <li class="info_lst"><ul> ... </ul></li>
//        Element infoUl = detailDoc.selectFirst("li.info_lst ul");
//        String status = "";
//        String genre = "";
//        String writer = "";
//        String publisher = "";
//        String ageRating = "";
//
//        if (infoUl != null) {
//            Elements lis = infoUl.select("li");
//
//            for (Element li : lis) {
//                String text = li.text().trim(); // 예: "완결", "청소년 이용불가", "글 작가명" 등
//
//                // 연재상태
//                if (text.contains("완결")) {
//                    status = "완결";
//                } else if (text.contains("연재중")) {
//                    status = "연재중";
//                }
//                // 작가 (ex: <li><span>글</span><a>연계</a></li>)
//                if (text.contains("글")) {
//                    Element anchor = li.selectFirst("a");
//                    if (anchor != null) {
//                        writer = anchor.text().trim();
//                    }
//                }
//                // 출판사
//                if (text.contains("출판사")) {
//                    Element anchor = li.selectFirst("a");
//                    if (anchor != null) {
//                        publisher = anchor.text().trim();
//                    }
//                }
//                // 청소년 이용불가, 전체 이용가 등
//                if (text.contains("이용가") || text.contains("이용불가")) {
//                    ageRating = text;
//                }
//                // 장르 (ex: BL, 로판, 무협 등)
//                if (genre.isEmpty()) {
//                    Element anchor = li.selectFirst("a");
//                    // "글" "출판사" 아닌 a 태그 => 장르 링크일 수도 있음
//                    if (anchor != null && !text.contains("글") && !text.contains("출판사")) {
//                        genre = anchor.text().trim();
//                    }
//                }
//            }
//        }
//        System.out.println("연재상태: " + status);
//        System.out.println("장르: " + genre);
//        System.out.println("글작가: " + writer);
//        System.out.println("출판사: " + publisher);
//        System.out.println("연령가: " + ageRating);
//
//        // 3) 줄거리
//        Elements synopsisDivs = detailDoc.select("div._synopsis");
//        if (synopsisDivs.isEmpty()) {
//            System.out.println("줄거리 없음: _synopsis를 찾을 수 없음");
//        } else {
//            // '마지막 _synopsis'만 가져오기
//            Element lastDiv = synopsisDivs.get(synopsisDivs.size() - 1);
//            String fullText = lastDiv.text().trim();
//            System.out.println("줄거리(마지막 _synopsis): " + fullText);
//        }// 3) 줄거리: <div class="_synopsis">가 여러 개 있을 수 있음(‘더보기 전’, ‘더보기 후’)
//
//    }
//
//    /**
//     * Cookie 헤더를 통해 인증된 상태로 JSoup GET
//     */
//    private static Document getDocumentWithCookies(String url, String cookieString) throws Exception {
//        return Jsoup.connect(url)
//                .header("Cookie", cookieString)
//                .userAgent("Mozilla/5.0")
//                .timeout(10000)
//                .get();
//    }
//}
