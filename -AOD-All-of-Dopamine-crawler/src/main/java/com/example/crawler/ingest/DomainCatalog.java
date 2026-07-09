package com.example.crawler.ingest;

import com.example.shared.entity.*;
import com.example.shared.repository.*;

import java.util.List;
import java.util.Optional;

/**
 * 도메인별로 다른 것 전부를 모은 테이블 — 이 파일 하나가 "도메인 추가 시 고칠 곳"이다.
 * (구 DomainCoreUpsertService의 switch 3개 + ContentMergeService의 후보검색 switch 흡수)
 * 새 도메인 추가 = Domain enum + 엔티티/리포지토리 + 아래 switch 4곳에 한 줄씩.
 */
public class DomainCatalog {

    private final MovieContentRepository movieRepo;
    private final TvContentRepository tvRepo;
    private final GameContentRepository gameRepo;
    private final WebtoonContentRepository webtoonRepo;
    private final WebnovelContentRepository webnovelRepo;

    public DomainCatalog(MovieContentRepository movieRepo, TvContentRepository tvRepo,
                         GameContentRepository gameRepo, WebtoonContentRepository webtoonRepo,
                         WebnovelContentRepository webnovelRepo) {
        this.movieRepo = movieRepo;
        this.tvRepo = tvRepo;
        this.gameRepo = gameRepo;
        this.webtoonRepo = webtoonRepo;
        this.webnovelRepo = webnovelRepo;
    }

    /** 도메인 엔티티 생성 (Content 연결, 저장 전). */
    public Object create(Domain domain, Content content) {
        return switch (domain) {
            case MOVIE -> new MovieContent(content);
            case TV -> new TvContent(content);
            case GAME -> new GameContent(content);
            case WEBTOON -> new WebtoonContent(content);
            case WEBNOVEL -> new WebnovelContent(content);
        };
    }

    /** 병합 시 기존 도메인 엔티티 로드. */
    public Optional<Object> findByContentId(Domain domain, Long contentId) {
        return switch (domain) {
            case MOVIE -> movieRepo.findById(contentId).map(e -> e);
            case TV -> tvRepo.findById(contentId).map(e -> e);
            case GAME -> gameRepo.findById(contentId).map(e -> e);
            case WEBTOON -> webtoonRepo.findById(contentId).map(e -> e);
            case WEBNOVEL -> webnovelRepo.findById(contentId).map(e -> e);
        };
    }

    /**
     * 중복 후보: 같은 author(웹툰/웹소설)·developer(게임)의 기존 작품들.
     * MOVIE/TV는 기존 시스템과 동일하게 미지원(빈 목록). author/developer 없으면 검색하지 않는다.
     */
    public List<Content> duplicateCandidates(Domain domain, Object entity) {
        return switch (domain) {
            case GAME -> {
                String dev = ((GameContent) entity).getDeveloper();
                yield isBlank(dev) ? List.of()
                        : gameRepo.findByDeveloper(dev).stream().map(GameContent::getContent).toList();
            }
            case WEBTOON -> {
                String author = ((WebtoonContent) entity).getAuthor();
                yield isBlank(author) ? List.of()
                        : webtoonRepo.findByAuthor(author).stream().map(WebtoonContent::getContent).toList();
            }
            case WEBNOVEL -> {
                String author = ((WebnovelContent) entity).getAuthor();
                yield isBlank(author) ? List.of()
                        : webnovelRepo.findByAuthor(author).stream().map(WebnovelContent::getContent).toList();
            }
            case MOVIE, TV -> List.of();
        };
    }

    /** 도메인 엔티티 저장 (신규·병합 공용). */
    public void save(Domain domain, Object entity) {
        switch (domain) {
            case MOVIE -> movieRepo.save((MovieContent) entity);
            case TV -> tvRepo.save((TvContent) entity);
            case GAME -> gameRepo.save((GameContent) entity);
            case WEBTOON -> webtoonRepo.save((WebtoonContent) entity);
            case WEBNOVEL -> webnovelRepo.save((WebnovelContent) entity);
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
