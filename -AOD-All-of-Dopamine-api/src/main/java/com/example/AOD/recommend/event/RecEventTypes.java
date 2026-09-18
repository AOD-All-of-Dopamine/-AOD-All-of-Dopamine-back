package com.example.AOD.recommend.event;

import java.util.Set;

/** 이벤트 이름 (REC_TAB_DESIGN §5-2). 규칙: {대상}_{과거형 동사}, 소문자 스네이크. */
public final class RecEventTypes {

    private RecEventTypes() { }

    /** 클라이언트가 POST /api/rec-events 로 보낼 수 있는 타입. 서버 타입은 위조 방지를 위해 받지 않는다. */
    public static final Set<String> CLIENT_TYPES = Set.of(
            "impression_viewed", "card_clicked", "detail_viewed", "outbound_clicked",
            "rec_loaded_more", "rec_tab_changed");
}
