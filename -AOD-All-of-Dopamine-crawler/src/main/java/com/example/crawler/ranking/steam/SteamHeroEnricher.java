package com.example.crawler.ranking.steam;

import com.example.crawler.contents.game.steam.portrait.SteamPortraitClient;
import com.example.crawler.contents.game.steam.portrait.SteamPortraitClient.Assets;
import com.example.crawler.ranking.steam.SteamHeroClient.SteamReview;
import com.example.shared.entity.ExternalRanking;
import com.example.shared.featured.FeaturedGates;
import com.example.shared.featured.ReviewQuotes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Steam 랭킹 행에 히어로 그림 · 리뷰 한 줄을 붙인다(저장 전). 실패는 "확인 안 함"으로 둬 옛 값을 유지한다.
 * <ul>
 *   <li>배경 — 모든 행, GetItems {@code library_hero}</li>
 *   <li>로고 · 인용 — 오늘의 작품 문턱을 넘는 30위 이내 행만(그 밖은 없음으로 확인)</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SteamHeroEnricher {

    private final SteamPortraitClient portraitClient;
    private final SteamHeroClient heroClient;

    public void enrich(List<ExternalRanking> rankings) {
        attachBackdrops(rankings);
        int logos = 0, quotes = 0, candidates = 0;
        for (ExternalRanking row : rankings) {
            if (!FeaturedGates.isCandidate(row)) {
                // 후보가 아니면 로고 · 인용을 비운다 — 문턱에서 떨어진 작품의 옛 인용이 남지 않게
                row.setLogoChecked(true);
                row.setQuoteChecked(true);
                continue;
            }
            candidates++;
            long appId = Long.parseLong(row.getPlatformSpecificId());
            try {
                Optional<SteamHeroClient.Logo> logo = heroClient.fetchLogo(appId);
                logo.ifPresent(l -> {
                    row.setLogoUrl(l.url());
                    row.setLogoLang(l.lang());
                });
                row.setLogoChecked(true);
                if (logo.isPresent()) logos++;
            } catch (Exception e) {
                log.warn("Steam 로고 조회 실패 appId={}: {}", appId, e.getMessage());
            }
            try {
                List<SteamReview> reviews = heroClient.fetchKoreanReviews(appId);
                Optional<ReviewQuotes.Picked<SteamReview>> picked = ReviewQuotes.pickSteam(reviews);
                if (picked.isPresent()) {
                    SteamReview r = picked.get().candidate();
                    row.setQuoteText(picked.get().text());
                    row.setQuoteAuthor(heroClient.fetchPersonaName(r.steamId()));
                    row.setQuoteVotes(r.votesUp());
                    row.setQuoteHours(Math.round(r.playtimeAtReviewMinutes() / 60f));
                    row.setQuoteUrl("https://steamcommunity.com/profiles/" + r.steamId() + "/recommended/" + appId + "/");
                    row.setQuoteReviewId(r.recommendationId());
                    quotes++;
                    log.info("Steam 리뷰 한 줄 appId={} ranking={} recommendationid={} \"{}\"", appId, row.getRanking(),
                            r.recommendationId(), abbreviate(picked.get().text()));
                }
                row.setQuoteChecked(true);
            } catch (Exception e) {
                log.warn("Steam 리뷰 조회 실패 appId={}: {}", appId, e.getMessage());
            }
        }
        log.info("Steam 히어로 — 배경 {}/{} · 후보 {} · 로고 {} · 리뷰 한 줄 {}",
                rankings.stream().filter(r -> r.getBackdropUrl() != null).count(), rankings.size(), candidates, logos, quotes);
    }

    private void attachBackdrops(List<ExternalRanking> rankings) {
        for (int from = 0; from < rankings.size(); from += SteamPortraitClient.MAX_BATCH) {
            List<ExternalRanking> batch = rankings.subList(from, Math.min(from + SteamPortraitClient.MAX_BATCH, rankings.size()));
            List<Long> ids = new ArrayList<>();
            for (ExternalRanking r : batch) ids.add(Long.parseLong(r.getPlatformSpecificId()));
            try {
                Map<Long, Assets> assets = portraitClient.fetchAssets(ids);
                for (ExternalRanking r : batch) {
                    Assets a = assets.get(Long.parseLong(r.getPlatformSpecificId()));
                    if (a == null) continue;                    // 응답에 없음 — 확인 안 함
                    r.setBackdropUrl(a.heroUrl());
                    r.setBackdropChecked(true);
                }
            } catch (Exception e) {
                log.warn("Steam 배경 조회 실패 — 옛 값을 둔다: {}", e.getMessage());
            }
        }
    }

    private static String abbreviate(String s) {
        return s.length() <= 30 ? s : s.substring(0, 30) + "…";
    }
}
