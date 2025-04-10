package com.example.AOD.StreamAPI.dto.GameDetailDto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class SteamAppDetail {
    private boolean success;
    private GameDetailDto data;
}
