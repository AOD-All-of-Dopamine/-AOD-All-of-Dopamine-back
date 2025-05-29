package com.example.AOD.commonV2.dto;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Data;
import lombok.NoArgsConstructor;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        property = "@type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = MovieTypeData.class, name = "movie"),
        @JsonSubTypes.Type(value = GameTypeData.class, name = "game"),
        @JsonSubTypes.Type(value = NovelTypeData.class, name = "novel"),
        @JsonSubTypes.Type(value = WebtoonTypeData.class, name = "webtoon"),
        @JsonSubTypes.Type(value = OTTTypeData.class, name = "ott")
})
@Data
@NoArgsConstructor
public abstract class TypeSpecificData {
    // 모든 타입에 공통으로 필요한 필드가 있다면 여기에 추가
}