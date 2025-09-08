package com.example.AOD.ingest;

import jakarta.persistence.*;
import lombok.Getter; import lombok.Setter;

import java.time.Instant;

@Entity @Getter @Setter
@Table(name="transform_runs", indexes = @Index(name="idx_tr_created", columnList="createdAt"))
public class TransformRun {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long runId;

    @Column(nullable=false) private Long rawId;
    @Column(nullable=false) private String platformName;
    @Column(nullable=false) private String domain;
    private String rulePath;           // ex: rules/webnovel/naverseries.yml

    @Column(nullable=false) private String status; // SUCCESS/FAILED
    @Column(columnDefinition="text") private String error;

    private Long producedContentId;
    private Instant createdAt = Instant.now();
    private Instant finishedAt;
}