package com.example.crawler.demo;

import com.example.shared.entity.Content;
import com.example.shared.entity.Domain;
import com.example.shared.repository.ContentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/test")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:3000")
public class TestDataController {

    private final ContentRepository contentRepository;

    /**
     * 신작 테스트용 더미 데이터 삽입
     */
    @PostMapping("/insert-dummy-data")
    public ResponseEntity<Map<String, Object>> insertDummyData() {
        log.info("=== 더미 데이터 삽입 시작 ===");
        
        List<Content> contents = new ArrayList<>();
        Instant now = Instant.now();

        // GAME 도메인 신작 (최근 1주일)
        contents.add(createContent(Domain.GAME, "엘든 링: 그림자의 왕", "Elden Ring: Shadow of the Erdtree", 
            LocalDate.of(2025, 10, 15),
            "https://cdn.cloudflare.steamstatic.com/steam/apps/1245620/header.jpg",
            "거대한 보스전과 새로운 던전이 가득한 확장팩", now));

        contents.add(createContent(Domain.GAME, "스타필드: 부서진 우주", "Starfield: Shattered Space", 
            LocalDate.of(2025, 10, 18),
            "https://cdn.cloudflare.steamstatic.com/steam/apps/1716740/header.jpg",
            "SF RPG의 새로운 확장 스토리", now));

        contents.add(createContent(Domain.GAME, "메타포: 리판타지오", "Metaphor: ReFantazio", 
            LocalDate.of(2025, 10, 11),
            "https://cdn.cloudflare.steamstatic.com/steam/apps/2679460/header.jpg",
            "페르소나 팀의 신작 판타지 RPG", now));

        // WEBTOON 도메인 신작 (최근 2주일)
        contents.add(createContent(Domain.WEBTOON, "나 혼자만 레벨업: 신의 귀환", "나 혼자만 레벨업: 신의 귀환", 
            LocalDate.of(2025, 10, 8),
            "https://image-comic.pstatic.net/webtoon/183559/thumbnail/thumbnail_IMAG21.jpg",
            "성진우의 새로운 모험이 시작된다", now));

        contents.add(createContent(Domain.WEBTOON, "화산귀환 2부", "화산귀환 2부", 
            LocalDate.of(2025, 10, 14),
            "https://image-comic.pstatic.net/webtoon/236342/thumbnail/thumbnail_IMAG21.jpg",
            "최강의 검마가 돌아왔다", now));

        contents.add(createContent(Domain.WEBTOON, "전지적 독자 시점: 외전", "전지적 독자 시점: 외전", 
            LocalDate.of(2025, 10, 21),
            "https://image-comic.pstatic.net/webtoon/119874/thumbnail/thumbnail_IMAG21.jpg",
            "김독자의 새로운 이야기", now));

        // WEBNOVEL 도메인 신작 (최근 1개월)
        contents.add(createContent(Domain.WEBNOVEL, "마법천자문: 귀환", "마법천자문: 귀환", 
            LocalDate.of(2025, 9, 25),
            "https://image.aladin.co.kr/product/33000/12/cover500/k052932636_1.jpg",
            "최강의 마법사가 과거로 돌아간다", now));

        contents.add(createContent(Domain.WEBNOVEL, "전생했더니 슬라임이었던 건에 대하여 20권", "転生したらスライムだった件", 
            LocalDate.of(2025, 10, 1),
            "https://image.aladin.co.kr/product/33000/12/cover500/k052932636_1.jpg",
            "리무루의 새로운 모험", now));

        contents.add(createContent(Domain.WEBNOVEL, "던전 디펜스: 재림", "던전 디펜스: 재림", 
            LocalDate.of(2025, 10, 10),
            "https://image.aladin.co.kr/product/33000/12/cover500/k052932636_1.jpg",
            "마왕이 된 주인공의 복수극", now));

        contents.add(createContent(Domain.WEBNOVEL, "나는 대마법사다", "나는 대마법사다", 
            LocalDate.of(2025, 10, 17),
            "https://image.aladin.co.kr/product/33000/12/cover500/k052932636_1.jpg",
            "현대에서 마법을 펼치다", now));

        // MOVIE 도메인 신작 (최근 1개월)
        contents.add(createContent(Domain.MOVIE, "듄: 파트 2", "Dune: Part Two", 
            LocalDate.of(2025, 10, 5),
            "https://m.media-amazon.com/images/M/MV5BN2QyZGU4ZDctOWMzMy00NTc5LThlOGQtODhmNDI1NmY5YzAwXkEyXkFqcGdeQXVyMDM2NDM2MQ@@._V1_.jpg",
            "폴 아트레이데스의 장대한 여정이 계속된다", now));

        contents.add(createContent(Domain.MOVIE, "데드풀과 울버린", "Deadpool & Wolverine", 
            LocalDate.of(2025, 9, 28),
            "https://m.media-amazon.com/images/M/MV5BNzRiMjg0MzUtNTQ1Mi00Y2Q5LWEwM2MtMzUwZDU5NmVjN2NkXkEyXkFqcGdeQXVyMTEzMTI1Mjk3._V1_.jpg",
            "MCU 최초의 데드풀과 울버린의 만남", now));

        contents.add(createContent(Domain.MOVIE, "베놈: 라스트 댄스", "Venom: The Last Dance", 
            LocalDate.of(2025, 10, 12),
            "https://m.media-amazon.com/images/M/MV5BZDMyYWU4NzItZDY0MC00ODE2LTkyYTMtMzNkNDdmYmFhZDg0XkEyXkFqcGdeQXVyMTEyNzgwMDUw._V1_.jpg",
            "베놈의 마지막 모험", now));

        contents.add(createContent(Domain.MOVIE, "조커: 폴리 아 되", "Joker: Folie à Deux", 
            LocalDate.of(2025, 10, 19),
            "https://m.media-amazon.com/images/M/MV5BYjZlMTA5ZGYtODVmNC00NDJmLWE2MWItZmQ3OTJlMDE2OTQ5XkEyXkFqcGdeQXVyMTUzMTg2ODkz._V1_.jpg",
            "조커와 할리 퀸의 광기 어린 로맨스", now));

        // 과거 작품 (비교용 - 신작이 아닌 것들)
        contents.add(createContent(Domain.GAME, "젤다의 전설: 왕국의 눈물", "The Legend of Zelda: Tears of the Kingdom", 
            LocalDate.of(2023, 5, 12),
            "https://cdn.cloudflare.steamstatic.com/steam/apps/1245620/header.jpg",
            "링크의 새로운 모험", now));

        contents.add(createContent(Domain.WEBTOON, "신의 탑", "신의 탑", 
            LocalDate.of(2010, 6, 30),
            "https://image-comic.pstatic.net/webtoon/20274/thumbnail/thumbnail_IMAG21.jpg",
            "탑을 오르는 소년 밤의 이야기", now));

        contents.add(createContent(Domain.WEBNOVEL, "달빛조각사", "달빛조각사", 
            LocalDate.of(2007, 1, 1),
            "https://image.aladin.co.kr/product/33000/12/cover500/k052932636_1.jpg",
            "최고의 가상현실 게임 소설", now));

        contents.add(createContent(Domain.MOVIE, "인터스텔라", "Interstellar", 
            LocalDate.of(2014, 11, 7),
            "https://m.media-amazon.com/images/M/MV5BZjdkOTU3MDktN2IxOS00OGEyLWFmMjktY2FiMmZkNWIyODZiXkEyXkFqcGdeQXVyMTMxODk2OTU@._V1_.jpg",
            "크리스토퍼 놀란의 SF 걸작", now));

        // 데이터베이스에 저장
        List<Content> savedContents = contentRepository.saveAll(contents);
        
        log.info("=== 더미 데이터 삽입 완료: {}개 ===", savedContents.size());

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "더미 데이터 삽입 완료");
        response.put("insertedCount", savedContents.size());
        response.put("contents", savedContents.stream()
            .map(c -> Map.of(
                "id", c.getContentId(),
                "title", c.getMasterTitle(),
                "domain", c.getDomain().name(),
                "releaseDate", c.getReleaseDate() != null ? c.getReleaseDate().toString() : "N/A"
            ))
            .toList());

        return ResponseEntity.ok(response);
    }

    /**
     * 모든 테스트 데이터 삭제
     */
    @DeleteMapping("/clear-test-data")
    public ResponseEntity<Map<String, Object>> clearTestData() {
        log.info("=== 테스트 데이터 삭제 시작 ===");
        
        long beforeCount = contentRepository.count();
        
        // 최근 6개월 이내의 데이터만 삭제 (안전장치)
        LocalDate sixMonthsAgo = LocalDate.now().minusMonths(6);
        List<Content> recentContents = contentRepository.findAll().stream()
            .filter(c -> c.getReleaseDate() != null && c.getReleaseDate().isAfter(sixMonthsAgo))
            .toList();
        
        contentRepository.deleteAll(recentContents);
        
        long afterCount = contentRepository.count();
        long deletedCount = beforeCount - afterCount;
        
        log.info("=== 테스트 데이터 삭제 완료: {}개 ===", deletedCount);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "테스트 데이터 삭제 완료");
        response.put("deletedCount", deletedCount);
        response.put("remainingCount", afterCount);

        return ResponseEntity.ok(response);
    }

    /**
     * 현재 contents 데이터 통계
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        long totalCount = contentRepository.count();
        LocalDate now = LocalDate.now();

        Map<Domain, Long> domainCounts = new HashMap<>();
        for (Domain domain : Domain.values()) {
            long count = contentRepository.findByDomain(domain, null).getTotalElements();
            domainCounts.put(domain, count);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("totalCount", totalCount);
        response.put("domainCounts", domainCounts);
        response.put("currentDate", now.toString());

        return ResponseEntity.ok(response);
    }

    private Content createContent(Domain domain, String masterTitle, String originalTitle,
                                 LocalDate releaseDate, String posterImageUrl, 
                                 String synopsis, Instant timestamp) {
        Content content = new Content();
        content.setDomain(domain);
        content.setMasterTitle(masterTitle);
        content.setOriginalTitle(originalTitle);
        content.setReleaseDate(releaseDate);
        content.setPosterImageUrl(posterImageUrl);
        content.setSynopsis(synopsis);
        content.setCreatedAt(timestamp);
        content.setUpdatedAt(timestamp);
        return content;
    }
}


