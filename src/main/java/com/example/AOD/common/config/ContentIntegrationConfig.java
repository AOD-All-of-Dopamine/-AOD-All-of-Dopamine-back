package com.example.AOD.common.config;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "content_integration_config")
@Data
@NoArgsConstructor
public class ContentIntegrationConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String contentType; // "movie", "novel", "game", "ott", "webtoon" 등

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(nullable = false)
    private boolean isActive;

    @OneToMany(mappedBy = "config", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<FieldMapping> fieldMappings = new ArrayList<>();

    @OneToMany(mappedBy = "config", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CustomFieldCalculation> customCalculations = new ArrayList<>();
}