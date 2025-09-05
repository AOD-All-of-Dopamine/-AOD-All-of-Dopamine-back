package com.example.AOD.v2.rules;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class NormalizerStep {
    private String type;
    private List<String> fields;
    // getters/setters
}