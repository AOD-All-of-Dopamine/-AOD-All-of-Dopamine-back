package com.example.AOD.Webtoon.NaverWebtoon.domain.dto;

public class WebtoonApiRequestDTO {
    private String keyword;
    private String provider;
    private String page;
    private String perPage;
    private String sort;
    private String isUpdated;
    private String isFree;
    private String updateDay;

    public WebtoonApiRequestDTO(String keyword, String provider, String page, String perPage, String sort,
                                String isUpdated,
                                String isFree, String updateDay) {
        this.keyword = keyword.isEmpty()?"":keyword;
        this.provider = provider.isEmpty()?"":provider;
        this.page = page.isEmpty()?"":page;
        this.perPage = perPage.isEmpty()?"100":perPage;
        this.sort = sort.isEmpty()?"":sort;
        this.isUpdated = isUpdated.isEmpty()?"":isUpdated;
        this.isFree = isFree.isEmpty()?"":isFree;
        this.updateDay = updateDay.isEmpty()?"":updateDay;
    }

    public String toQueryString(){
        return String.format(
                "keyword=%s&provider=%s&page=%s&perPage=%s&sort=%s&isUpdated=%s&isFree=%s&updateDay=%s",
                keyword, provider, page, perPage, sort, isUpdated, isFree, updateDay
        );
    }


}
