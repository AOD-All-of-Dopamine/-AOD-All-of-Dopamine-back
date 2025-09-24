package com.example.AOD.repo;

import com.example.AOD.domain.Content;
import com.example.AOD.domain.entity.Domain;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ContentRepository extends JpaRepository<Content, Long> {
    Optional<Content> findFirstByDomainAndMasterTitleAndReleaseYear(Domain domain, String masterTitle, Integer releaseYear);

    // [✨ NEW] 도메인별 페이징 조회를 위한 메서드 추가
    Page<Content> findByDomain(Domain domain, Pageable pageable);
}