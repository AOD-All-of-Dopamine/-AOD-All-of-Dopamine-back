package com.example.shared.repository;

import com.example.shared.entity.Content;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;

import java.util.List;

/**
 * {@link ContentRepositoryCustom} 구현 — {@link WorksQueryBuilder}가 조립한 네이티브 SQL을 실행한다.
 *
 * count 쿼리는 Spring Data의 @Query Page 경로와 동일하게 {@link PageableExecutionUtils}가
 * "첫 페이지가 다 안 찼을 때"는 생략한다 (troubleshooting/07 §8 ③의 Slice 전환 전까지의 완화).
 */
public class ContentRepositoryImpl implements ContentRepositoryCustom {

    @PersistenceContext
    private EntityManager em;

    @Override
    public Page<Content> findWorks(WorksFilterCriteria criteria, Pageable pageable) {
        WorksQueryBuilder.Built built = WorksQueryBuilder.build(criteria);

        Query main = em.createNativeQuery(built.sql(), Content.class);
        built.params().forEach(main::setParameter);
        main.setFirstResult((int) pageable.getOffset());
        main.setMaxResults(pageable.getPageSize());

        @SuppressWarnings("unchecked")
        List<Content> rows = main.getResultList();

        return PageableExecutionUtils.getPage(rows, pageable, () -> {
            Query count = em.createNativeQuery(built.countSql());
            built.params().forEach(count::setParameter);
            return ((Number) count.getSingleResult()).longValue();
        });
    }
}
