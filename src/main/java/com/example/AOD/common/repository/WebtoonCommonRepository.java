package com.example.AOD.common.repository;

import com.example.AOD.common.commonDomain.WebtoonCommon;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WebtoonCommonRepository extends JpaRepository<WebtoonCommon, Long> {
    List<WebtoonCommon> findByTitleIgnoreCase(String title);
    List<WebtoonCommon> findByTitleContainingIgnoreCase(String keyword);
}
