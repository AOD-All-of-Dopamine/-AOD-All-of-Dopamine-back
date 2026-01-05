package com.example.crawler.contents.TMDB.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
public class WatchProviderResult {

    @JsonProperty("id")
    private int id;

    @JsonProperty("results")
    private Map<String, CountryProviders> results;

    @Getter
    @Setter
    @NoArgsConstructor
    public static class CountryProviders {

        @JsonProperty("link")
        private String link;

        @JsonProperty("flatrate")
        private List<Provider> flatrate; // 구독

        @JsonProperty("rent")
        private List<Provider> rent; // 대여

        @JsonProperty("buy")
        private List<Provider> buy; // 구매
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Provider {

        @JsonProperty("logo_path")
        private String logoPath;

        @JsonProperty("provider_id")
        private int providerId;

        @JsonProperty("provider_name")
        private String providerName;

        @JsonProperty("display_priority")
        private int displayPriority;
    }
}

