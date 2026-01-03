package com.example.crawler.ingest;

import jakarta.persistence.*;
import lombok.Getter; import lombok.Setter;

import java.time.Instant;

@Entity @Getter @Setter
@Table(name="transform_runs", indexes = @Index(name="idx_tr_created", columnList="createdAt"))
public class TransformRun {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long runId;

    @Column(nullable=false) private Long rawId;
    @Column(nullable=false, length=100) private String platformName;
    @Column(nullable=false, length=50) private String domain;
    @Column(length=500) private String rulePath;           // ex: rules/webnovel/naverseries.yml

    @Column(nullable=false, length=50) private String status; // SUCCESS/FAILED
    @Column(columnDefinition="text") private String error;

    private Long producedContentId;
    private Instant createdAt = Instant.now();
    private Instant finishedAt;
}


