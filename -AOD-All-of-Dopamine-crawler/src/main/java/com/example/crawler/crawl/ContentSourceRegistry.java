package com.example.crawler.crawl;

import com.example.crawler.common.queue.JobType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * JobType -> ContentSource registry, auto-populated from all ContentSource beans.
 * Mirrors JobExecutorRegistry. Adding a platform = add one ContentSource bean; no change here.
 */
@Slf4j
@Component
public class ContentSourceRegistry {

    private final Map<JobType, ContentSource<?>> byJobType = new EnumMap<>(JobType.class);

    public ContentSourceRegistry(List<ContentSource<?>> sources) {
        for (ContentSource<?> source : sources) {
            JobType jobType = source.descriptor().jobType();
            ContentSource<?> previous = byJobType.put(jobType, source);
            if (previous != null) {
                throw new IllegalStateException("Duplicate ContentSource for " + jobType + ": "
                        + previous.getClass().getSimpleName() + " vs " + source.getClass().getSimpleName());
            }
            log.info("📌 [Registry] ContentSource 등록: {} -> {}",
                    jobType, source.getClass().getSimpleName());
        }
    }

    public ContentSource<?> get(JobType jobType) {
        ContentSource<?> source = byJobType.get(jobType);
        if (source == null) {
            throw new IllegalArgumentException("No ContentSource for jobType: " + jobType);
        }
        return source;
    }

    public Map<JobType, ContentSource<?>> all() {
        return new EnumMap<>(byJobType);
    }
}
