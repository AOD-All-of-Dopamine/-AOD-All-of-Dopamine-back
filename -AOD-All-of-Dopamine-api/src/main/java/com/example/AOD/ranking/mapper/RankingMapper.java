package com.example.AOD.ranking.mapper;

import com.example.shared.entity.Content;
import com.example.AOD.ranking.dto.RankingResponse;
import com.example.shared.entity.ExternalRanking;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * ExternalRanking 엔티티를 RankingResponse DTO로 변환
 * 
 * 변환 규칙:
 * - id: Long → Long (그대로 전달)
 * - contentId: Content.contentId → Long (매핑된 경우만, 없으면 null)
 * - ranking: Integer → Integer (그대로 전달)
 * - thumbnailUrl: String → String (그대로 전달)
 * - content: Content → ContentInfo (중첩 객체 변환, 매핑된 경우만)
 */
@Component
public class RankingMapper {

    /**
     * 단일 엔티티를 DTO로 변환
     * 
     * @param entity ExternalRanking 엔티티
     * @return RankingResponse DTO
     */
    public RankingResponse toResponse(ExternalRanking entity) {
        RankingResponse response = new RankingResponse();
        response.setId(entity.getId());
        response.setTitle(entity.getTitle());
        response.setRanking(entity.getRanking());
        response.setPlatform(entity.getPlatform());
        response.setThumbnailUrl(entity.getThumbnailUrl());
        response.setWatchProviders(entity.getWatchProviders());
        
        // Content 매핑 정보가 있는 경우
        if (entity.getContent() != null) {
            Content content = entity.getContent();
            
            // contentId 설정 (프론트엔드 호환)
            response.setContentId(content.getContentId());
            
            // 상세 정보 설정 (선택적)
            RankingResponse.ContentInfo contentInfo = new RankingResponse.ContentInfo();
            contentInfo.setContentId(content.getContentId());
            contentInfo.setDomain(content.getDomain().name());
            contentInfo.setMasterTitle(content.getMasterTitle());
            contentInfo.setPosterImageUrl(content.getPosterImageUrl());
            response.setContent(contentInfo);
        }
        
        return response;
    }

    /**
     * 엔티티 리스트를 DTO 리스트로 변환
     * 
     * @param entities ExternalRanking 엔티티 리스트
     * @return RankingResponse DTO 리스트
     */
    public List<RankingResponse> toResponseList(List<ExternalRanking> entities) {
        return entities.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }
}


