package com.example.AOD.shared;

import com.example.shared.featured.ReviewQuotes;
import com.example.shared.featured.ReviewQuotes.Verdict;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 히어로 리뷰 한 줄 거름 — 검수 실측 표본(설계 2026-10-03 "ReviewQuotes"). */
class ReviewQuotesTest {

    private static String reason(String text) {
        Verdict v = ReviewQuotes.judgeText(text);
        return v.ok() ? "통과" : v.reason();
    }

    @Test
    void passesAndNormalizes() {
        Verdict v = ReviewQuotes.judgeText("미션 하러가야지 -> 여긴 뭐지? -> 아 맞다 미션 ->  무한 반복 시간녹는 게임");
        assertThat(v.ok()).isTrue();
        assertThat(v.text()).isEqualTo("미션 하러가야지 → 여긴 뭐지? → 아 맞다 미션 → 무한 반복 시간녹는 게임");
        assertThat(reason("진짜 내 인생 게임 총 맞아도 되니까 나이트 시티에서 살고싶음")).isEqualTo("통과");
    }

    @Test
    void profanityIncludingInitialsAndSpacingBypass() {
        assertThat(reason("개연성이 좆박았니 하는 무위키 피셜은 믿지 마세요 진짜 명작")).isEqualTo("욕설");
        assertThat(reason("보스전만 피하시길 나머지는 ㅈㄴ 재밌음 진짜 강추하는 게임")).isIn("욕설", "스포일러 의심");
        assertThat(reason("나머지는 ㅈ ㄴ 재밌음 진짜 강추하는 게임입니다 여러분")).isEqualTo("욕설");      // 띄어쓰기 우회
        assertThat(reason("이건 진짜 시.발 미친 게임이다 다들 꼭 해보세요 제발")).isEqualTo("욕설");       // 문장부호 우회
    }

    @Test
    void spoilerTagRejectedBeforeStripping() {
        // 태그를 지우고 거르면 "범인은 …" 같은 내용이 남는다 — 태그가 있으면 먼저 탈락
        assertThat(reason("[spoiler]주인공 친구가 배신함[/spoiler] 그래도 갓겜이니 꼭 하세요")).isEqualTo("스포일러 태그");
        assertThat(reason("마지막 결말에서 눈물이 났습니다 꼭 끝까지 해보세요")).isEqualTo("스포일러 의심");
    }

    @Test
    void bbcodeAndUrlAreStrippedForDisplay() {
        Verdict v = ReviewQuotes.judgeText("[h1]안녕, V.[/h1] 절대 싸움을 멈추지 마. https://example.com 최고의 게임");
        assertThat(v.ok()).isTrue();
        assertThat(v.text()).isEqualTo("안녕, V. 절대 싸움을 멈추지 마. 최고의 게임");
    }

    @Test
    void negativeSignalInRecommendedReview() {
        assertThat(reason("앞으로는 미완성 게임 출시하지 말아주세요 그래도 추천은 함")).isEqualTo("부정 신호");
    }

    @Test
    void lengthAndRepeat() {
        assertThat(reason("갓겜 강추")).isEqualTo("너무 짧음");
        assertThat(reason("가".repeat(71))).isEqualTo("너무 김");
        assertThat(reason("ㅋㅋㅋㅋㅋㅋㅋㅋ 이 게임 진짜 재밌어요 다들 해보세요")).isEqualTo("반복");
    }

    @Test
    void steamRules() {
        String ok = "초월적인 더빙, 개성 있는 캐릭터들, 나이트 시티의 향수. 여기가 나의 고향 같다.";
        assertThat(ReviewQuotes.judgeSteam(ok, false, 100, 0, 600).reason()).isEqualTo("비추천");
        assertThat(ReviewQuotes.judgeSteam(ok, true, 9, 0, 600).reason()).isEqualTo("도움 10 미만");
        assertThat(ReviewQuotes.judgeSteam(ok, true, 20, 11, 600).reason()).isEqualTo("밈");
        assertThat(ReviewQuotes.judgeSteam(ok, true, 20, 0, 119).reason()).isEqualTo("플레이 2시간 미만");
        assertThat(ReviewQuotes.judgeSteam(ok, true, 20, 0, 120).ok()).isTrue();
    }

    record C(String text, boolean votedUp, int votesUp, int votesFunny, double weightedScore, int playtimeAtReviewMinutes)
            implements ReviewQuotes.SteamCandidate { }

    @Test
    void picksMostHelpfulPassingOne() {
        var picked = ReviewQuotes.pickSteam(List.of(
                new C("초월적인 더빙, 개성 있는 캐릭터들, 나이트 시티의 향수. 여기가 나의 고향 같다.", true, 29, 0, 0.9, 600),
                new C("이 게임 진짜 좆같이 재밌어요 다들 꼭 해보세요 진심으로", true, 300, 0, 0.9, 600),
                new C("진짜 내 인생 게임 총 맞아도 되니까 나이트 시티에서 살고싶음", true, 45, 0, 0.5, 600),
                new C("진짜 내 인생 게임 총 맞아도 되니까 나이트 시티에서 살고싶다", true, 45, 0, 0.8, 600)));
        assertThat(picked).isPresent();
        assertThat(picked.get().text()).endsWith("살고싶다");   // 300(욕설) 다음 45 중 가중 점수 높은 것
        assertThat(ReviewQuotes.pickSteam(List.<C>of())).isEmpty();
    }
}
