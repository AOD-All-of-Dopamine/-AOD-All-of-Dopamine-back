package com.example.shared.featured;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 홈 "오늘의 작품" 리뷰 한 줄 — 정리 · 거름 (설계 2026-10-03-home-featured-hero-design.md "ReviewQuotes").
 * 낱말 목록은 classpath {@code quote-blocklist.txt}(섹션 [profanity] · [spoiler] · [negative]).
 *
 * <p>거름 순서가 중요하다 — {@code [spoiler]} 는 BBCode 를 지우기 <b>전에</b> 본다(지우면 내용만 남는다).
 */
public final class ReviewQuotes {

    public static final int MIN_LENGTH = 20;
    public static final int MAX_LENGTH = 70;

    private static final Pattern SPOILER_TAG = Pattern.compile("\\[/?spoiler]", Pattern.CASE_INSENSITIVE);
    private static final Pattern BBCODE = Pattern.compile("\\[/?[a-zA-Z0-9*]+(?:=[^\\]]*)?]");
    private static final Pattern URL = Pattern.compile("https?://\\S+");
    private static final Pattern SPACES = Pattern.compile("\\s+");
    private static final Pattern NOT_LETTER = Pattern.compile("[^\\p{L}\\p{N}]");
    private static final Pattern REPEAT = Pattern.compile("(.)\\1{6,}");

    private static final Lists LISTS = load();

    private ReviewQuotes() { }

    /** 거름 결과 — 통과면 {@code text} 에 표시용 글. */
    public record Verdict(boolean ok, String reason, String text) {
        static Verdict reject(String reason) { return new Verdict(false, reason, null); }
    }

    /** 표시용 정리 — BBCode · URL 제거, 공백 하나로, {@code ->} 를 {@code →} 로. {@code [spoiler]} 판정 전에 쓰지 말 것. */
    public static String normalize(String raw) {
        if (raw == null) return "";
        String s = BBCODE.matcher(raw).replaceAll(" ");
        s = URL.matcher(s).replaceAll(" ");
        s = s.replace("->", "→").replace("=>", "→");
        return SPACES.matcher(s).replaceAll(" ").trim();
    }

    /** 글만 보는 거름(외부 · 우리 리뷰 공통). */
    public static Verdict judgeText(String raw) {
        if (raw == null || raw.isBlank()) return Verdict.reject("빈 글");
        if (SPOILER_TAG.matcher(raw).find()) return Verdict.reject("스포일러 태그");
        String text = normalize(raw);
        int len = text.codePointCount(0, text.length());
        if (len < MIN_LENGTH) return Verdict.reject("너무 짧음");
        if (len > MAX_LENGTH) return Verdict.reject("너무 김");
        String lower = text.toLowerCase(Locale.ROOT);
        String squashed = NOT_LETTER.matcher(lower).replaceAll("");   // 띄어쓰기 · 문장부호 우회 막기
        for (String w : LISTS.profanitySquash) {
            if (lower.contains(w) || squashed.contains(w)) return Verdict.reject("욕설");
        }
        for (String w : LISTS.profanity) {
            if (containsWord(lower, w)) return Verdict.reject("욕설");
        }
        for (String w : LISTS.spoiler) {
            if (containsWord(lower, w)) return Verdict.reject("스포일러 의심");
        }
        for (String w : LISTS.negative) {
            if (lower.contains(w)) return Verdict.reject("부정 신호");
        }
        if (REPEAT.matcher(text).find() || text.codePoints().distinct().count() < 12) return Verdict.reject("반복");
        return new Verdict(true, null, text);
    }

    /** 영어 낱말은 낱말 경계로("this hit" 의 "shit" 같은 우연 일치 막기), 그 밖은 포함 여부. */
    private static boolean containsWord(String text, String word) {
        if (!word.chars().allMatch(c -> c < 128)) return text.contains(word);
        return Pattern.compile("(?<![a-z])" + Pattern.quote(word) + "(?![a-z])").matcher(text).find();
    }

    /** Steam 리뷰 하나 — 글 거름 + 추천 · 도움 · 밈 · 플레이 시간. */
    public static Verdict judgeSteam(String review, boolean votedUp, int votesUp, int votesFunny, int playtimeAtReviewMinutes) {
        if (!votedUp) return Verdict.reject("비추천");
        if (votesUp < 10) return Verdict.reject("도움 10 미만");
        if (votesFunny > votesUp * 0.5) return Verdict.reject("밈");
        if (playtimeAtReviewMinutes < 120) return Verdict.reject("플레이 2시간 미만");
        return judgeText(review);
    }

    /** Steam 후보들 중 고르기 — 도움 많은 순, 같으면 가중 점수 높은 순으로 처음 통과한 것. */
    public static <T extends SteamCandidate> Optional<Picked<T>> pickSteam(List<T> candidates) {
        List<T> sorted = new ArrayList<>(candidates);
        sorted.sort((a, b) -> {
            int c = Integer.compare(b.votesUp(), a.votesUp());
            return c != 0 ? c : Double.compare(b.weightedScore(), a.weightedScore());
        });
        for (T c : sorted) {
            Verdict v = judgeSteam(c.text(), c.votedUp(), c.votesUp(), c.votesFunny(), c.playtimeAtReviewMinutes());
            if (v.ok()) return Optional.of(new Picked<>(c, v.text()));
        }
        return Optional.empty();
    }

    public interface SteamCandidate {
        String text();
        boolean votedUp();
        int votesUp();
        int votesFunny();
        double weightedScore();
        int playtimeAtReviewMinutes();
    }

    public record Picked<T>(T candidate, String text) { }

    // ---------- 낱말 목록 ----------

    private record Lists(List<String> profanitySquash, List<String> profanity, List<String> spoiler, List<String> negative) { }

    private static Lists load() {
        List<String> profanitySquash = new ArrayList<>(), profanity = new ArrayList<>(), spoiler = new ArrayList<>(),
                negative = new ArrayList<>();
        List<String> current = null;
        try (InputStream in = ReviewQuotes.class.getClassLoader().getResourceAsStream("quote-blocklist.txt")) {
            if (in == null) throw new IllegalStateException("quote-blocklist.txt 가 없다");
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            for (String line; (line = reader.readLine()) != null; ) {
                String t = line.trim();
                if (t.isEmpty() || t.startsWith("#")) continue;
                switch (t) {
                    case "[profanity-squash]" -> current = profanitySquash;
                    case "[profanity]" -> current = profanity;
                    case "[spoiler]" -> current = spoiler;
                    case "[negative]" -> current = negative;
                    default -> {
                        if (current != null) current.add(t.toLowerCase(Locale.ROOT));
                    }
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("quote-blocklist.txt 읽기 실패", e);
        }
        return new Lists(Collections.unmodifiableList(profanitySquash), Collections.unmodifiableList(profanity),
                Collections.unmodifiableList(spoiler), Collections.unmodifiableList(negative));
    }
}
