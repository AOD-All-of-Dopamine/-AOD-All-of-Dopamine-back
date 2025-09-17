package com.example.AOD.service;


import com.example.AOD.Webtoon.NaverWebtoon.repository.WebtoonRepository;
import com.example.AOD.domain.Content;
import com.example.AOD.domain.entity.*;
import com.example.AOD.repo.AvContentRepository;
import com.example.AOD.repo.GameContentRepository;
import com.example.AOD.repo.WebnovelContentRepository;
import com.example.AOD.repo.WebtoonContentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class DomainCoreUpsertService {

    private final GameContentRepository gameRepo;
    private final WebtoonContentRepository webtoonRepo;
    private final WebnovelContentRepository webnovelRepo;
    private final AvContentRepository avRepo;

    public DomainCoreUpsertService(GameContentRepository gameRepo,
                                   WebtoonContentRepository webtoonRepo,
                                   WebnovelContentRepository webnovelRepo,
                                   AvContentRepository avRepo) {

        this.gameRepo = gameRepo;
        this.webtoonRepo = webtoonRepo;
        this.webnovelRepo = webnovelRepo;
        this.avRepo = avRepo;
    }

    private LocalDate parseDate(Object s) {
        if (s == null) return null;
        String v = s.toString().trim();
        // [수정] "yyyy년 M월 d일" 패턴 추가 및 Locale.KOREAN 설정
        String[] patterns = {"uuuu년 M월 d일", "yyyy-MM-dd","yyyy.MM.dd","yyyy/MM/dd","MMM d, yyyy"};
        for (String p : patterns) {
            try {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern(p, Locale.KOREAN);
                return LocalDate.parse(v, formatter);
            }
            catch (Exception ignored) {}
        }
        try { return LocalDate.of(Integer.parseInt(v), 1, 1); } catch (Exception ignored) {}
        return null;
    }

    @Transactional
    public void upsert(Domain domain, Content content, Map<String,Object> domainDoc) {
        if (domainDoc == null || domainDoc.isEmpty()) return;

        switch (domain) {
            case AV -> {
                AvContent av = avRepo.findById(content.getContentId()).orElseGet(() -> {
                    AvContent x = new AvContent();
                    x.setContent(content);
                    return x;
                });
                if (domainDoc.get("tmdb_id") instanceof Number num) av.setTmdbId(num.intValue());
                if (domainDoc.get("av_type") != null) av.setAvType(domainDoc.get("av_type").toString());
                if (domainDoc.get("release_date") != null) av.setReleaseDate(parseDate(domainDoc.get("release_date")));

                if (domainDoc.get("release_date") != null) av.setReleaseDate(parseDate(domainDoc.get("release_date")));
                if (domainDoc.get("runtime") instanceof Number num) av.setRuntimeMin(num.intValue());

                if (domainDoc.get("cast") instanceof List castList) av.setCastMembers(Map.of("cast", castList));
                if (domainDoc.get("crew") instanceof List crewList) av.setCrewMembers(Map.of("crew", crewList));

                if (domainDoc.get("season_count") instanceof Number num) av.setSeasonCount(num.intValue());

                if (domainDoc.get("genres") instanceof List<?> list) {
                    av.setGenres(Map.of("tmdb_genres", list));
                }
                avRepo.save(av);
            }
            case GAME -> {
                GameContent g = gameRepo.findById(content.getContentId()).orElseGet(() -> {
                    GameContent x = new GameContent();
                    x.setContent(content);
                    return x;
                });
                if (domainDoc.get("developer") != null) g.setDeveloper(domainDoc.get("developer").toString());
                if (domainDoc.get("publisher") != null) g.setPublisher(domainDoc.get("publisher").toString());
                if (domainDoc.get("release_date") != null) g.setReleaseDate(parseDate(domainDoc.get("release_date")));
                if (domainDoc.get("platforms") instanceof Map<?,?> m)
                    g.setPlatforms((Map<String,Object>) m);

                // [수정] List 타입으로 받고 Map으로 감싸서 저장
                if (domainDoc.get("genres") instanceof List<?> list) {
                    g.setGenres(Map.of("steam_genres", list));
                }
                gameRepo.save(g);
            }
            case WEBTOON -> {
                WebtoonContent w = webtoonRepo.findById(content.getContentId()).orElseGet(() -> {
                    WebtoonContent x = new WebtoonContent();
                    x.setContent(content);
                    return x;
                });
                if (domainDoc.get("author") != null) w.setAuthor(domainDoc.get("author").toString());
                if (domainDoc.get("illustrator") != null) w.setIllustrator(domainDoc.get("illustrator").toString());
                if (domainDoc.get("status") != null) w.setStatus(domainDoc.get("status").toString());
                if (domainDoc.get("started_at") != null) w.setStartedAt(parseDate(domainDoc.get("started_at")));
                if (domainDoc.get("genres") instanceof Map<?,?> m)
                    w.setGenres((Map<String,Object>) m);
                webtoonRepo.save(w);
            }
            case WEBNOVEL -> {
                WebnovelContent wn = webnovelRepo.findById(content.getContentId()).orElseGet(() -> {
                    WebnovelContent x = new WebnovelContent();
                    x.setContent(content);
                    return x;
                });
                if (domainDoc.get("author") != null) wn.setAuthor(domainDoc.get("author").toString());
                if (domainDoc.get("translator") != null) wn.setTranslator(domainDoc.get("translator").toString());
                if (domainDoc.get("started_at") != null) wn.setStartedAt(parseDate(domainDoc.get("started_at")));
                if (domainDoc.get("genres") instanceof Map<?,?> m)
                    wn.setGenres((Map<String,Object>) m);
                webnovelRepo.save(wn);
            }
            default -> { /* AV는 TMDB로 처리 */ }
        }
    }
}