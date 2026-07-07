package com.example.crawler.rules;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class DomainObjectMapping {
    // 값을 채워 넣을 대상 엔티티의 필드(속성) 이름
    private String targetField;
    // 값의 타입 (예: string, integer, date 등). 추후 타입 변환 로직에 사용 가능
    private String type;
    // RF-5: 원본 값 → 도메인 값 치환표 (예: {"true": 완결, "false": 연재중}).
    // 도메인 특화 변환을 범용 변환기에 하드코딩하지 않고 yml로 선언한다. 맵에 없는 값은 원본 유지.
    private java.util.Map<String, String> valueMap;
}


