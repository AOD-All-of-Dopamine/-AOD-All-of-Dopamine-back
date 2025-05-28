package com.example.AOD.commonV2.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class GameTypeData extends TypeSpecificData {
    private List<String> developers;
    private List<String> publishers;
    private List<String> genres;
    private Long requiredAge;
    private String summary;
    private Integer initialPrice;
    private Integer finalPrice;
}