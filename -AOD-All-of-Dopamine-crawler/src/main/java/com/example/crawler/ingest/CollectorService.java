package com.example.crawler.ingest;

import com.example.shared.entity.RawItem;
import com.example.shared.repository.RawItemRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

//Raw Item 으로 저장하는 로직
@Slf4j
@Service @RequiredArgsConstructor
public class CollectorService {

    private final RawItemRepository rawRepo;
    private final ObjectMapper om = new ObjectMapper();

    @Transactional
    public Long saveRaw(String platformName, String domain,
                        Map<String,Object> payload,
                        String platformSpecificId, String url) {
        
        // platformSpecificId 유효성 검증
        if (platformSpecificId == null || "null".equals(platformSpecificId) 
            || platformSpecificId.trim().isEmpty()) {
            log.error("❌ [유효성 검증 실패] 유효하지 않은 platformSpecificId: '{}', Platform: {}, Domain: {}", 
                    platformSpecificId, platformName, domain);
            return -1L; // 저장하지 않음
        }
        
        String hash = sha256Canonical(payload);
        
        // 1차: platformName + platformSpecificId로 기존 데이터 검색 (같은 콘텐츠 찾기)
        Optional<RawItem> existingByPlatformId = rawRepo.findByPlatformNameAndPlatformSpecificId(
                platformName, platformSpecificId);
        
        if (existingByPlatformId.isPresent()) {
            RawItem existing = existingByPlatformId.get();
            String oldHash = existing.getHash();
            
            // 데이터가 변경되었는지 해시로 확인
            if (oldHash.equals(hash)) {
                // 완전히 동일한 데이터 → 아무것도 안 함
                log.info("⚠️  [중복 감지] 동일한 데이터가 이미 존재 (변경 없음) - Platform: {}, Domain: {}, ID: {}", 
                        platformName, domain, platformSpecificId);
                return existing.getRawId();
            } else {
                // 데이터가 변경됨 (vote_count, popularity 등) → 업데이트
                log.info("🔄 [데이터 갱신] 기존 데이터 업데이트 - Platform: {}, Domain: {}, ID: {}, OldHash: {}, NewHash: {}",
                        platformName, domain, platformSpecificId, 
                        oldHash.substring(0, 8) + "...", hash.substring(0, 8) + "...");
                
                // payload와 hash 업데이트, processed는 false로 재설정
                existing.setSourcePayload(payload);
                existing.setHash(hash);
                existing.setUrl(url);
                existing.setFetchedAt(Instant.now());
                existing.setProcessed(false);  // 재처리 필요
                existing.setProcessedAt(null);
                
                rawRepo.save(existing);
                return existing.getRawId();
            }
        }
        
        // 2차: 혹시 모를 해시 충돌 체크 (다른 platformSpecificId인데 같은 hash)
        Optional<RawItem> existingByHash = rawRepo.findByHash(hash);
        if (existingByHash.isPresent()) {
            RawItem existing = existingByHash.get();
            log.warn("⚠️  [해시 충돌 감지] 다른 콘텐츠인데 같은 해시 - 기존: {}/{}, 신규: {}/{}", 
                    existing.getPlatformName(), existing.getPlatformSpecificId(),
                    platformName, platformSpecificId);
            // 해시 충돌이지만 다른 콘텐츠이므로 새로 저장
        }
        
        // 신규 데이터 저장
        RawItem r = new RawItem();
        r.setPlatformName(platformName);
        r.setDomain(domain);
        r.setSourcePayload(payload);
        r.setPlatformSpecificId(platformSpecificId);
        r.setUrl(url);
        r.setHash(hash);
        Long savedId = rawRepo.save(r).getRawId();
        log.info("✅ [신규 저장] raw_items에 저장 완료 - Platform: {}, Domain: {}, ID: {}, RawId: {}",
                platformName, domain, platformSpecificId, savedId);
        return savedId;
    }

    private String sha256Canonical(Map<String,Object> payload){
        try {
            byte[] json = om.writeValueAsBytes(payload); // 캐논라이즈
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(json);
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
}


