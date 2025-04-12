package com.example.AOD.game.StreamAPI.dto.GameDetailDto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PriceOverview {
    //실 가격 = 금액/100
    private String currency;
    private int initial;
    @JsonProperty("final")
    private int final_;
    private int discount_percent;
    private String initial_formatted;
    private String final_formatted;
}