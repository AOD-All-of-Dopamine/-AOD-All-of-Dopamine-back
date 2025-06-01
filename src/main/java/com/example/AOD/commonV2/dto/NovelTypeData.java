package com.example.AOD.commonV2.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class NovelTypeData extends TypeSpecificData {
    private List<String> authors;
    private List<String> genres;
    private String status;  // 연재상태
    private String publisher;
    private String ageRating;
}