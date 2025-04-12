package com.example.AOD.OTT.Netflix.domain;


import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * 장르 정보를 저장하는 엔티티
 */
@Getter
@Setter
@Entity
@Table(name = "genre")
public class Genre {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String name;
}
