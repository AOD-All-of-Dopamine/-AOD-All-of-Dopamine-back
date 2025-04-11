package com.example.AOD.NaverSeriesNovel.config;

import com.example.AOD.NaverSeriesNovel.crawler.NaverSeriesCrawler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class NaverSeriesConfig {

    @Bean
    public NaverSeriesCrawler naverSeriesCrawler(){
        return new NaverSeriesCrawler();
    }
}
