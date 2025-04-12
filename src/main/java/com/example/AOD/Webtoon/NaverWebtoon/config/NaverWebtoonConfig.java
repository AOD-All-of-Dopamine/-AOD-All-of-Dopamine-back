package com.example.AOD.Webtoon.NaverWebtoon.config;

import com.example.AOD.Webtoon.NaverWebtoon.crawler.NaverWebtoonCrawler;
import com.example.AOD.Webtoon.NaverWebtoon.fetcher.WebtoonApiFetcher;
import com.example.AOD.Webtoon.NaverWebtoon.util.ChromeDriverProvider;
import com.example.AOD.Webtoon.NaverWebtoon.util.NaverLoginHandler;
import org.springframework.context.annotation.Bean;

//@Configuration
public class NaverWebtoonConfig {
    @Bean
    public ChromeDriverProvider ChromeDriverProvider(){
        return new ChromeDriverProvider();
    }

    @Bean
    public WebtoonApiFetcher WebtoonApiFetcher(){
        return new WebtoonApiFetcher();
    }

    @Bean
    public NaverLoginHandler NaverLoginHandler(){
        return new NaverLoginHandler();
    }

    @Bean
    public NaverWebtoonCrawler NaverWebtoonCrawler(){
        return new NaverWebtoonCrawler();
    }

}
