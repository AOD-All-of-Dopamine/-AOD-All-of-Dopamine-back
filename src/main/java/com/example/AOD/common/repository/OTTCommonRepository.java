package com.example.AOD.common.repository;

import com.example.AOD.common.commonDomain.OTTCommon;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OTTCommonRepository extends JpaRepository<OTTCommon, Long> {
    List<OTTCommon> findByTitleIgnoreCase(String title);
    List<OTTCommon> findByTitleContainingIgnoreCase(String keyword);
}
