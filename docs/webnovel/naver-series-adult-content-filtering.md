# 네이버 시리즈 19금 작품 필터링 구현

## 📋 문제 상황

네이버 시리즈 웹소설 크롤링 시, 19금 작품의 경우 상세 페이지 접근이 제한되어 다음과 같은 문제가 발생했습니다:

- **현상**: "네이버" 또는 빈 제목으로 엔티티가 생성됨
- **원인**: 19금 작품 페이지가 로그인 페이지로 리다이렉트되면서 `og:title`이 "네이버"로 설정됨
- **영향**: 의미 없는 데이터가 DB에 저장되어 데이터 품질 저하

---

## 🔍 API 응답 분석

### 테스트 API 요청

**PowerShell 명령어:**
```powershell
# 19금 작품 요청
Invoke-WebRequest -Uri "https://series.naver.com/novel/detail.series?productNo=13564952" `
    -UserAgent "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36" `
    -UseBasicParsing

# 일반 작품 요청
Invoke-WebRequest -Uri "https://series.naver.com/novel/detail.series?productNo=13522869" `
    -UserAgent "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36" `
    -UseBasicParsing
```

**Java (Jsoup) 코드:**
```java
Document doc = Jsoup.connect("https://series.naver.com/novel/detail.series?productNo=13564952")
    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36")
    .referrer("https://series.naver.com/")
    .timeout(15000)
    .get();
```

---

### 19금 작품 (예: productNo=13564952)

**HTTP 응답:**
- **Status Code**: 200 OK
- **Content-Type**: text/html; charset=utf-8
- **실제 페이지**: 네이버 로그인 페이지로 리다이렉트 (HTML 내부 리다이렉트)

**응답 특징:**
- 네이버 로그인 페이지로 리다이렉트
- 연령 확인 메시지 표시

**핵심 HTML 요소:**
```html
<!DOCTYPE html>
<html lang="ko">
<head>
    <meta charset="UTF-8">
    <meta property="og:type" content="website">
    <!-- ⚠️ 제목이 기본값 "네이버"로 설정됨 -->
    <meta property="og:title" content="네이버">
    <meta property="og:description" content="네이버에 로그인 하고 나를 위한 다양한 서비스를 이용해 보세요">
    <title>네이버 : 로그인</title>
</head>
<body>
    <!-- ✅ 1. 연령 확인 메시지 (핵심 판별 포인트) -->
    <div class="top_message_wrap">
        <span class="message_text" id="adult_msg">
            서비스 이용을 위해 연령 확인이 필요합니다.<br> 로그인 후 이용해주세요.
        </span>
    </div>

    <!-- ✅ 2. 암호화 타입 표시 (핵심 판별 포인트) -->
    <input type="hidden" name="enctp" id="enctp" value="19">

    <!-- 3. 원본 URL 유지 -->
    <input type="hidden" id="adult_surl_v2" name="adult_surl_v2" 
           value="http://series.naver.com/novel/detail.series?productNo=13564952">
    
    <!-- 4. 로그인 폼 -->
    <form id="frmNIDLogin" name="frmNIDLogin" action="https://nid.naver.com/nidlogin.login" method="POST">
        <input type="text" id="id" name="id" title="아이디" class="input_id">
        <input type="password" id="pw" name="pw" title="비밀번호" class="input_pw">
        <!-- ... -->
    </form>
</body>
</html>
```

**JSON 형태 요약:**
```json
{
  "statusCode": 200,
  "isAdultContent": true,
  "indicators": {
    "adult_msg": "존재",
    "enctp": "19",
    "og:title": "네이버",
    "actualPage": "로그인 페이지"
  },
  "title": "네이버",
  "description": "네이버에 로그인 하고 나를 위한 다양한 서비스를 이용해 보세요",
  "originalUrl": "http://series.naver.com/novel/detail.series?productNo=13564952"
}
```

### 일반 작품 (예: productNo=13522869)

**HTTP 응답:**
- **Status Code**: 200 OK
- **Content-Type**: text/html; charset=utf-8
- **실제 페이지**: 작품 상세 페이지

**응답 특징:**
- 정상 상세 페이지 표시
- 작품 정보 완전히 노출
- `adult_msg` 요소 없음
- `enctp` 필드 없음

**핵심 HTML 요소:**
```html
<!DOCTYPE html>
<html lang="ko">
<head>
    <meta charset="UTF-8">
    <!-- ✅ 실제 작품 제목 -->
    <meta property="og:title" content="검룰 아니고 찐재 변호사입니다">
    <meta property="og:description" content="...">
    <meta property="og:image" content="https://comicthumb-phinf.pstatic.net/...">
    <title>검룰 아니고 찐재 변호사입니다 : 네이버 시리즈</title>
</head>
<body>
    <!-- ⚠️ adult_msg 없음 (19금 아님) -->
    <!-- ⚠️ enctp 필드 없음 (19금 아님) -->
    
    <!-- ✅ 작품 상세 정보 존재 -->
    <div class="end_head">
        <h2 class="end_tit">검룰 아니고 찐재 변호사입니다</h2>
        <div class="score_area">
            <em class="num">9.59</em>
        </div>
        <p class="end_dsc">
            관심 2억 5,006만
            댓글 1,393,475
        </p>
    </div>
    
    <!-- 작품 정보 -->
    <ul class="end_info">
        <li><span>글</span> 작가명</li>
        <li><span>출판사</span> 출판사명</li>
        <li><span>이용가</span> 전체 이용가</li>
        <li>장르 분류</li>
    </ul>
    
    <!-- 권호 목록 -->
    <table id="volumeList">
        <!-- 회차 정보 -->
    </table>
    
    <script type="text/javascript">
        var ghtProductInfo = {
            "sService" : "novel",
            "nProductNo" : 13522869,
            "nTotalVolumeCount" : 125,
            "bPcPossible" : true,
            // ...
        };
    </script>
</body>
</html>
```

**JSON 형태 요약:**
```json
{
  "statusCode": 200,
  "isAdultContent": false,
  "indicators": {
    "adult_msg": "없음",
    "enctp": "없음",
    "og:title": "검룰 아니고 찐재 변호사입니다",
    "actualPage": "작품 상세 페이지"
  },
  "title": "검룰 아니고 찐재 변호사입니다",
  "author": "작가명",
  "publisher": "출판사명",
  "rating": 9.59,
  "downloadCount": 250060000,
  "commentCount": 1393475,
  "episodeCount": 125,
  "ageRating": "전체 이용가",
  "genres": ["장르1", "장르2"],
  "synopsis": "작품 설명...",
  "imageUrl": "https://comicthumb-phinf.pstatic.net/...",
  "productUrl": "https://series.naver.com/novel/detail.series?productNo=13522869"
}
```

---

## ✅ 구현 전략

### 1. 19금 작품 판별 로직

두 가지 조건 중 **하나라도 만족**하면 19금으로 판단:

| 조건 | 선택자 | 판별 기준 |
|------|--------|----------|
| **연령 확인 메시지** | `#adult_msg` | 요소 존재 여부 |
| **암호화 타입** | `input[name=enctp]` | value가 "19"인 경우 |

### 2. 필터링 시점

- **위치**: 상세 페이지 파싱 직후 (제목 추출 전)
- **동작**: 19금 판별 시 즉시 `continue`로 다음 작품으로 스킵
- **로그**: INFO 레벨로 스킵 사유와 URL 기록

### 3. 로그 출력

```java
log.info("19금 작품으로 스킵: {}", detailUrl);
```

---

## 🛠️ 코드 변경 사항

### NaverSeriesCrawler.java

**변경 위치**: `crawlToRaw()` 메서드 내 for-loop

**Before:**
```java
for (String detailUrl : detailUrls) {
    Document doc = get(detailUrl, cookieString);
    
    String productUrl = attr(doc.selectFirst("meta[property=og:url]"), "content");
    if (productUrl == null || productUrl.isBlank()) productUrl = detailUrl;

    String rawTitle = attr(doc.selectFirst("meta[property=og:title]"), "content");
    String title = cleanTitle(rawTitle != null ? rawTitle : text(doc.selectFirst("h2")));
    // ... 이후 처리
}
```

**After:**
```java
for (String detailUrl : detailUrls) {
    Document doc = get(detailUrl, cookieString);

    // 19금 작품 체크: adult_msg 또는 enctp="19" 존재 여부로 판단
    Element adultMsg = doc.selectFirst("#adult_msg");
    Element enctp = doc.selectFirst("input[name=enctp]");
    boolean isAdultContent = (adultMsg != null) || 
                           (enctp != null && "19".equals(enctp.attr("value")));
    
    if (isAdultContent) {
        log.info("19금 작품으로 스킵: {}", detailUrl);
        continue;
    }

    String productUrl = attr(doc.selectFirst("meta[property=og:url]"), "content");
    if (productUrl == null || productUrl.isBlank()) productUrl = detailUrl;

    String rawTitle = attr(doc.selectFirst("meta[property=og:title]"), "content");
    String title = cleanTitle(rawTitle != null ? rawTitle : text(doc.selectFirst("h2")));
    // ... 이후 처리
}
```

**추가된 import:**
```java
import lombok.extern.slf4j.Slf4j;
```

**추가된 어노테이션:**
```java
@Slf4j
@Component
public class NaverSeriesCrawler {
    // ...
}
```

---

## 🧪 검증 방법

### 1. 19금 작품 테스트

**테스트 URL:**
```
https://series.naver.com/novel/detail.series?productNo=13564952
```

**예상 동작:**
1. 크롤러가 상세 페이지 요청
2. `#adult_msg` 또는 `enctp="19"` 감지
3. INFO 로그 출력: `"19금 작품으로 스킵: https://series.naver.com/novel/detail.series?productNo=13564952"`
4. 해당 작품 스킵, 다음 작품으로 진행

**로그 예시:**
```
2025-12-31 10:00:00 [INFO ] c.e.AOD.c.Novel.NaverSeriesNovel.NaverSeriesCrawler - 19금 작품으로 스킵: https://series.naver.com/novel/detail.series?productNo=13564952
```

### 2. 일반 작품 테스트

**테스트 URL:**
```
https://series.naver.com/novel/detail.series?productNo=13522869
```

**예상 동작:**
1. 19금 체크 통과 (adult_msg 없음, enctp≠"19")
2. 정상적으로 제목 및 상세 정보 추출
3. `raw_items` 테이블에 저장

---

## 📊 효과

### Before (필터링 없음)
- ❌ "네이버"라는 제목의 빈 엔티티 다수 생성
- ❌ 의미 없는 데이터로 DB 용량 낭비
- ❌ 검색 및 추천 품질 저하

### After (필터링 적용)
- ✅ 19금 작품 자동 스킵
- ✅ 유효한 데이터만 저장
- ✅ 데이터 품질 향상
- ✅ 로그로 스킵 내역 추적 가능

---

## 🔐 보안 고려사항

### 현재 구현의 한계
- 쿠키 없이 크롤링하므로 19금 작품은 접근 불가
- 로그인 기반 크롤링 시 별도 구현 필요

### 향후 확장 가능성
1. **로그인 크롤링**: 쿠키 인증을 통한 19금 작품 수집
2. **연령 메타데이터 저장**: `ageRating` 필드에 "19세 이용가" 저장
3. **선택적 필터링**: 설정에 따라 19금 수집 여부 결정

---

## 📝 관련 문서

- [네이버 시리즈 크롤링 가이드](../README.md)
- [데이터 품질 관리](../../optimization/data-quality.md)
- [크롤링 최적화](../../optimization/crawling-improvements.md)

---

**작성일**: 2025-12-31  
**작성자**: AI Assistant  
**버전**: 1.0.0
