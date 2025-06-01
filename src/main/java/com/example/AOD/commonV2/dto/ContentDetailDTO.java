package com.example.AOD.commonV2.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ContentDetailDTO extends ContentDTO {
    private TypeSpecificData specificData;  // 타입별 특수 데이터
    private Map<String, PlatformInfo> platforms;  // 플랫폼 상세 정보

    @Data
    @NoArgsConstructor
    public static class PlatformInfo {
        private Long platformId;
        private String platformUrl;  // 선택적

        public PlatformInfo(Long platformId, String platformUrl) {
            this.platformId = platformId;
            this.platformUrl = platformUrl;
        }

        public PlatformInfo(Long platformId) {
            this.platformId = platformId;
        }
    }
}