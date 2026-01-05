package com.example.shared.entity;

import com.vladmihalcea.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.Getter; import lombok.Setter;
import org.hibernate.annotations.Type;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Entity @Getter @Setter
@Table(name = "raw_items", indexes = {
        @Index(name="idx_raw_proc", columnList = "processed,fetchedAt"),
        @Index(name="idx_platform_id", columnList = "platformName,platformSpecificId")  // 중복 검사용 인덱스
}, uniqueConstraints = @UniqueConstraint(name="uk_raw_hash", columnNames = {"hash"}))
public class RawItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long rawId;

    @Column(nullable=false) private String platformName;   // e.g. NaverSeries
    @Column(nullable=false) private String domain;         // e.g. WEBNOVEL

    // 원본 payload (크롤러가 그대로 넣음)
    @Type(JsonType.class) @Column(columnDefinition="jsonb", nullable=false)
    private Map<String,Object> sourcePayload = new HashMap<>();

    // 편의 필드(있으면 UpsertService에 그대로 넘김)
    private String platformSpecificId; // 예: titleId, seriesId, productNo
    private String url;

    @Column(nullable=false) private String hash;           // payload 해시(중복 방지)
    @Column(nullable=false) private Instant fetchedAt = Instant.now();

    @Column(nullable=false) private boolean processed = false;
    private Instant processedAt;
}

