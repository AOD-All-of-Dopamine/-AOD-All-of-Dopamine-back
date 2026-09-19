package com.example.AOD.recommend.card;

import java.util.List;

/** 조립 결과 — 고른 카드와 버린 후보. */
public record Assembly(List<AssembledCard> cards, List<DroppedCandidate> dropped) { }
