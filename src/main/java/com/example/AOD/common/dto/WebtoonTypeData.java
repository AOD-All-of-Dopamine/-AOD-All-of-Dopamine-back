package com.example.AOD.common.dto;

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
    private List<String> uploadDays;
    private String summary;
    private String publishDate;
}
