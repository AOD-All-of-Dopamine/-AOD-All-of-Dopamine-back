package com.example.AOD.NaverWebtoonCrawler.config;

import com.example.AOD.NaverWebtoonCrawler.crawler.NaverWebtoonCrawler;
import com.example.AOD.NaverWebtoonCrawler.fetcher.WebtoonApiFetcher;
import com.example.AOD.NaverWebtoonCrawler.util.ChromeDriverProvider;
import com.example.AOD.NaverWebtoonCrawler.util.NaverLoginHandler;
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
