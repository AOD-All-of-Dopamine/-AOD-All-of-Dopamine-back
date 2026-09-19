package com.example.AOD.repo;

import java.time.LocalDateTime;

/**
 * 사용자 상호작용 경량 행 (JPQL 생성자 표현식 대상).
 * 엔티티(ContentLike·Bookmark)를 싣지 않는 이유: content·user 가 LAZY 라 목록을 받으면 프록시가 줄줄이 붙는다.
 */
public record UserContentRow(Long contentId, LocalDateTime at) { }
