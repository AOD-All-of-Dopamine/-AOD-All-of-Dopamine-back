package com.example.AOD.common.repository;

import com.example.AOD.common.config.CustomFieldCalculation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CustomFieldCalculationRepository extends JpaRepository<CustomFieldCalculation, Long> {
    List<CustomFieldCalculation> findByConfigId(Long configId);
}