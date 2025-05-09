package com.example.AOD.common.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlatformInfoDTO {
    private String id;
    private String name;
    private String className;
}