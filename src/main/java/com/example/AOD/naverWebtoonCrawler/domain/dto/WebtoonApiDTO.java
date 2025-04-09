package com.example.AOD.naverWebtoonCrawler.domain.dto;

import java.util.ArrayList;
import lombok.Getter;
import com.example.AOD.naverWebtoonCrawler.domain.Days;

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
