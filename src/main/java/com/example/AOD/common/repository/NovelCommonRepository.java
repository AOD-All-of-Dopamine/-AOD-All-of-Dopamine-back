package com.example.AOD.common.repository;

import com.example.AOD.common.commonDomain.NovelCommon;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NovelCommonRepository extends JpaRepository<NovelCommon, Long> {
}