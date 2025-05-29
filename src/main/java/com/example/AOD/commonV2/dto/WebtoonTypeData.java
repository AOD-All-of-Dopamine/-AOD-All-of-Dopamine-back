package com.example.AOD.commonV2.dto;

import com.example.AOD.Webtoon.NaverWebtoon.domain.Days;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class WebtoonTypeData extends TypeSpecificData {
    private List<String> authors;
    private List<String> genres;
    private List<Days> uploadDays;
    private String summary;
    private String publishDate;
}