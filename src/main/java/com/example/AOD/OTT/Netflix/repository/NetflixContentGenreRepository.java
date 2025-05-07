package com.example.AOD.OTT.Netflix.repository;

import com.example.AOD.OTT.Netflix.domain.NetflixContentGenre;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface NetflixContentGenreRepository extends JpaRepository<NetflixContentGenre, Long> {


    Optional<NetflixContentGenre> findByName(String genreName);
}
