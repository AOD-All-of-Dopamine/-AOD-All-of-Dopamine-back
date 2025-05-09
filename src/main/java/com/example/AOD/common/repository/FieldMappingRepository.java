package com.example.AOD.common.repository;

import com.example.AOD.common.config.FieldMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FieldMappingRepository extends JpaRepository<FieldMapping, Long> {
    List<FieldMapping> findByConfigIdOrderByPriorityAsc(Long configId);
    List<FieldMapping> findByConfigIdAndCommonField(Long configId, String commonField);
    List<FieldMapping> findByConfigIdAndPlatform(Long configId, String platform);
}