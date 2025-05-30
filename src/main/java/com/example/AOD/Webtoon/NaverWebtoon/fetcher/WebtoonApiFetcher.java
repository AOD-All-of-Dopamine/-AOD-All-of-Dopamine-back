package com.example.AOD.Webtoon.NaverWebtoon.fetcher;

import com.example.AOD.Webtoon.NaverWebtoon.domain.dto.WebtoonApiRequestDTO;
import com.example.AOD.Webtoon.NaverWebtoon.domain.dto.WebtoonApiResponseDTO;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class WebtoonApiFetcher {

    private final String BASE_URL = "https://korea-webtoon-api-cc7dda2f0d77.herokuapp.com/webtoons?";

    private final RestTemplate restTemplate = new RestTemplate();

    public WebtoonApiResponseDTO fetchAll(WebtoonApiRequestDTO req) {
        String url = BASE_URL + req.toQueryString();
        return restTemplate.getForObject(url, WebtoonApiResponseDTO.class);
    }

}
