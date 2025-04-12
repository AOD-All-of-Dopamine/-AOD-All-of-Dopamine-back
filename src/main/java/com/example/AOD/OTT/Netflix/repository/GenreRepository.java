package com.example.AOD.OTT.Netflix.repository;

import com.example.AOD.OTT.Netflix.domain.Genre;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface GenreRepository extends JpaRepository<Genre, Long> {


    Optional<Genre> findByName(String genreName);
}
