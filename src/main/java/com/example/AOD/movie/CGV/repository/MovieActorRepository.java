package com.example.AOD.movie.CGV.repository;

import com.example.AOD.movie.CGV.domain.MovieActor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MovieActorRepository extends JpaRepository<MovieActor, Long> {
    Optional<MovieActor> findByName(String name);
}