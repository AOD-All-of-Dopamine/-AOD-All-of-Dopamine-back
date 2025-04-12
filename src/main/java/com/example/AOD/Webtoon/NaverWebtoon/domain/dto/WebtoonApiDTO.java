package com.example.AOD.Webtoon.NaverWebtoon.domain.dto;

import com.example.AOD.Webtoon.NaverWebtoon.domain.Days;
import java.util.ArrayList;
import lombok.Getter;

@Getter
public class WebtoonApiDTO {
    private String naverId;
    private String title;
    private String provider;
    private ArrayList<Days> updateDays;
    private String url;
    private ArrayList<String> thumbnail;
    private boolean isEnd;
    private boolean isFree;
    private boolean isUpdated;
    private int ageGrade;
    private int freeWaitHour;
    private ArrayList<String> authors;
}
