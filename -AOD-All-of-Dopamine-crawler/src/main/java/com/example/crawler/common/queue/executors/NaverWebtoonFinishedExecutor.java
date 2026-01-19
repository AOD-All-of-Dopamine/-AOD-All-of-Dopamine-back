package com.example.crawler.common.queue.executors;

import com.example.crawler.common.queue.JobExecutor;
import com.example.crawler.common.queue.JobType;
import com.example.crawler.contents.Webtoon.NaverWebtoon.NaverWebtoonService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 네이버 완결 웹툰 크롤링 Executor
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NaverWebtoonFinishedExecutor implements JobExecutor {

    private final NaverWebtoonService naverWebtoonService;

    @Override
    public JobType getJobType() {
        return JobType.NAVER_WEBTOON_FINISHED;
    }

    @Override
    public boolean execute(String targetId) {
        return naverWebtoonService.collectWebtoonById(targetId);
    }

    @Override
    public long getAverageExecutionTime() {
        return 5000; // Selenium 기반, 평균 5초
    }
}
