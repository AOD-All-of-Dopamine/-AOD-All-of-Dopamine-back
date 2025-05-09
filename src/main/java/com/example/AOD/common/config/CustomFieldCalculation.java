package com.example.AOD.common.config;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "custom_field_calculation")
@Data
@NoArgsConstructor
public class CustomFieldCalculation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "config_id", nullable = false)
    private ContentIntegrationConfig config;

    @Column(nullable = false)
    private String targetField; // 계산 결과가 저장될 Common 엔티티의 필드명

    @Column(nullable = false)
    private String calculationType; // 계산 유형 (e.g., "AVERAGE", "MAX", "CUSTOM")

    @Column(length = 1000)
    private String calculationExpression; // 커스텀 계산 로직 (필요한 경우)

    @Column(nullable = false)
    private Boolean isRequired; // 이 계산이 Common 생성에 필수인지 여부
}