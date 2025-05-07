package com.example.AOD.OTT.Netflix.domain;


import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * 특징(Features) 정보를 저장하는 엔티티
 */
@Getter
@Setter
@Entity
@Table(name = "feature")
public class NetflixContentFeature {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String name;
}
