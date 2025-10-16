package com.example.AOD.ingest;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

//Raw Item 으로 저장하는 로직
@Service @RequiredArgsConstructor
public class CollectorService {

    private final RawItemRepository rawRepo;
    private final ObjectMapper om = new ObjectMapper();

    @Transactional
    public Long saveRaw(String platformName, String domain,
                        Map<String,Object> payload,
                        String platformSpecificId, String url) {
        String hash = sha256Canonical(payload);
        return rawRepo.findByHash(hash)
                .map(RawItem::getRawId)
                .orElseGet(() -> {
                    RawItem r = new RawItem();
                    r.setPlatformName(platformName);
                    r.setDomain(domain);
                    r.setSourcePayload(payload);
                    r.setPlatformSpecificId(platformSpecificId);
                    r.setUrl(url);
                    r.setHash(hash);
                    return rawRepo.save(r).getRawId();
                });
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