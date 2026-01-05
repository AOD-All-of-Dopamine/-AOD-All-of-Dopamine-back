package com.example.crawler.rules;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class DomainObjectMapping {
    // 값을 채워 넣을 대상 엔티티의 필드(속성) 이름
    private String targetField;
    // 값의 타입 (예: string, integer, date 등). 추후 타입 변환 로직에 사용 가능
    private String type;
}


