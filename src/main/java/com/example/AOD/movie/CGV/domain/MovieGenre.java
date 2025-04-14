package com.example.AOD.movie.CGV.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "movie_genres")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MovieGenre {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    // 영화와의 다대다 관계 (양방향)
    @ManyToMany(mappedBy = "genres")
    private List<Movie> movies = new ArrayList<>();

    public MovieGenre(String name) {
        this.name = name;
    }
}
