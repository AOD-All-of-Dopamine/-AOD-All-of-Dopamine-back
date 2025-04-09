package com.example.AOD.naverWebtoonCrawler.domain;

import java.util.ArrayList;

public enum Days {
    MON("월"), TUE("화"), WED("수"), THU("목"), FRI("금"), SAT("토"), SUN("일");

    private final String label;

    Days(String label){
        this.label = label;
    }
    public static ArrayList<Days> parseDays(String str){
        ArrayList<Days> days = new ArrayList<>();

        for (Days day : Days.values()) {
            if (str.contains(day.label)) {
                days.add(day);
            }
        }
        return days;
    }
}
