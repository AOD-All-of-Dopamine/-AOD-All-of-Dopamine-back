package com.example.shared.repository;

/**
 * platform_data 경량 조회 행 (JPQL 생성자 표현식 대상).
 * 엔티티를 싣지 않는 이유: PlatformData.content 가 LAZY 라 엔티티 목록을 받으면 프록시 초기화로 N+1 이 난다.
 */
public record PlatformKeyRow(Long contentId, String platformName, String platformSpecificId, Long platformDataId) { }
