package com.example.AOD.game.StreamAPI.dto.GameDetailDto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class Screenshot {
    private int id;
    private String path_thumbnail;
    private String path_full;
}