package com.example.AOD.v2.repo;

import com.example.AOD.v2.domain.Content;
import com.example.AOD.v2.domain.entity.Domain;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ContentRepository extends JpaRepository<Content, Long> {
    Optional<Content> findFirstByDomainAndMasterTitleAndReleaseYear(Domain domain, String masterTitle, Integer releaseYear);
}
