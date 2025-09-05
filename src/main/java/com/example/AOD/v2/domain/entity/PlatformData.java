package com.example.AOD.v2.domain.entity;

import com.example.AOD.v2.domain.Content;
import com.vladmihalcea.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Type;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Entity
@Getter
@Setter
@Table(name = "platform_data",
        uniqueConstraints = @UniqueConstraint(name="uk_platform_id", columnNames = {"platformName","platformSpecificId"}))
public class PlatformData {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long platformDataId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "content_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_platform_data_content"))
    private Content content;

    @Column(nullable = false)
    private String platformName;

    private String platformSpecificId;
    private String url;

    // 평점/리뷰수는 플랫폼 기준, 마스터와 별개
    private BigDecimal rating;
    private Integer reviewCount;

    @Type(JsonType.class)
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> attributes = new HashMap<>();

    private Instant lastSeenAt = Instant.now();

    // getters/setters ...
}