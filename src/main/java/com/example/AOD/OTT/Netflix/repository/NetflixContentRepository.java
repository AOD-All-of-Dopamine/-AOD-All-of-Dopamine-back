package com.example.AOD.OTT.Netflix.repository;

import com.example.AOD.OTT.Netflix.domain.NetflixContent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface NetflixContentRepository extends JpaRepository<NetflixContent, Long> {
    Optional<NetflixContent> findById(Long id);
}
