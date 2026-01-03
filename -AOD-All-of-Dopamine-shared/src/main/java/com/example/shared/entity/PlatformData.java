// src/main/java/com/example/AOD/domain/entity/PlatformData.java
package com.example.shared.entity;


import com.vladmihalcea.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Type;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Entity
@Getter @Setter
@Table(name = "platform_data",
        uniqueConstraints = @UniqueConstraint(name="uk_platform_id", columnNames = {"platformName","platformSpecificId"}))
public class PlatformData {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long platformDataId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "content_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_platform_data_content"))
    private Content content;

    @Column(nullable = false, length = 100)
    private String platformName;       // 예: NAVER_SERIES

    @Column(length = 200)
    private String platformSpecificId; // 예: productNo
    @Column(length = 1000)
    private String url;                // 작품 상세 URL

    @Type(JsonType.class)
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> attributes = new HashMap<>();
    // 예) {
    //   "rating": 8.7,
    //   "comment_count": 1393475,
    //   "download_count": 724450000,     // (관심 수를 다운로드 수로 사용)
    //   "author": "비가",
    //   "publisher": "러프미디어",
    //   "status": "연재중",
    //   "age_rating": "전체 이용가",
    //   "genres": ["무협"],
    //   "synopsis": "...",
    //   "image_url": "...",
    //   ...
    // }

    private Instant lastSeenAt = Instant.now();
}
