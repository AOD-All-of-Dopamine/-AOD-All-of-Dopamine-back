package com.example.AOD.naverWebtoonCrawler.config;

import com.example.AOD.naverWebtoonCrawler.crawler.NaverWebtoonCrawler;
import com.example.AOD.naverWebtoonCrawler.fetcher.WebtoonApiFetcher;
import com.example.AOD.naverWebtoonCrawler.util.ChromeDriverProvider;
import com.example.AOD.naverWebtoonCrawler.util.NaverLoginHandler;
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
