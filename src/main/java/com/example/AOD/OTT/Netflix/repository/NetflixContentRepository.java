package com.example.AOD.OTT.Netflix.repository;

import com.example.AOD.OTT.Netflix.domain.NetflixContent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NetflixContentRepository extends JpaRepository<NetflixContent, Long> {
}
