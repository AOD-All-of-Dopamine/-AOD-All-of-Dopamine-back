package com.example.AOD.recommend.card;

import com.example.AOD.recommend.router.dto.RouterItem;
import com.example.shared.entity.Content;

/** 화면에 나갈 카드 1장 — 라우터 항목(점수·주도 시드)과 DB 작품을 함께 들고 있다. */
public record AssembledCard(RouterItem item, Content content) { }
