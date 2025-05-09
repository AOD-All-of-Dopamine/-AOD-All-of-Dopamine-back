package com.example.AOD.common.config;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "field_mapping")
@Data
@NoArgsConstructor
public class FieldMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "config_id", nullable = false)
    private ContentIntegrationConfig config;

    @Column(nullable = false)
    private String commonField; // Common 엔티티의 필드명

    @Column(nullable = false)
    private String platform; // 소스 플랫폼 (e.g., "naver", "kakao")

    @Column(nullable = false)
    private String platformField; // 플랫폼 엔티티의 필드명

    private Integer priority; // 동일한 commonField에 대해 여러 매핑이 있을 경우 우선순위
}