package com.example.crawler.ingest;

import com.example.crawler.ingest.rule.RuleRegistry;
import com.example.shared.repository.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** ingest 파이프라인 배선. 컴포넌트들은 plain 클래스 — 여기서만 Spring에 연결된다. */
@Configuration
public class IngestConfig {

    @Bean
    public DomainCatalog domainCatalog(MovieContentRepository movieRepo, TvContentRepository tvRepo,
                                       GameContentRepository gameRepo, WebtoonContentRepository webtoonRepo,
                                       WebnovelContentRepository webnovelRepo) {
        return new DomainCatalog(movieRepo, tvRepo, gameRepo, webtoonRepo, webnovelRepo);
    }

    @Bean
    public DraftAssembler draftAssembler(DomainCatalog catalog) {
        return new DraftAssembler(catalog);
    }

    @Bean
    public RuleRegistry ingestRuleRegistry(DomainCatalog catalog) {
        return new RuleRegistry("classpath*:rules/**/*.yml", catalog);  // 기동 검증 = 부팅 게이트
    }

    @Bean
    public IngestPipeline ingestPipeline(RawItemRepository rawRepo, TransformRunRepository runRepo,
                                         RuleRegistry ingestRuleRegistry, DraftAssembler draftAssembler,
                                         DomainCatalog domainCatalog, ContentRepository contentRepo,
                                         PlatformDataRepository platformRepo, PlatformTransactionManager txManager) {
        return new IngestPipeline(rawRepo, runRepo, ingestRuleRegistry, draftAssembler,
                domainCatalog, contentRepo, platformRepo, new TransactionTemplate(txManager));
    }
}
