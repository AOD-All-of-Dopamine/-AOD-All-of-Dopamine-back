package com.example.AOD.commonV2.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class OTTTypeData extends TypeSpecificData {
    private String type;  // series, movie
    private String creator;
    private List<String> actors;
    private List<String> genres;
    private List<String> features;
    private String description;
    private String maturityRating;
    private String releaseYear;
}