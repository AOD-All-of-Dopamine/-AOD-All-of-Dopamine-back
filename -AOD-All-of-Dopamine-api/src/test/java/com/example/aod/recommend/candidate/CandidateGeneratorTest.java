package com.example.AOD.recommend.candidate;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CandidateGeneratorTest {

    private final AiAssetRepository repo = mock(AiAssetRepository.class);
    private final CandidateGenerator gen = new CandidateGenerator(repo);

    @Test
    void coldStartUsesOnlyQualityFallback() {
        when(repo.findQualityFallback(5)).thenReturn(List.of(10L, 11L, 12L));
        List<Long> ids = gen.generate(null, new String[0], null, new String[0], 5);
        assertEquals(List.of(10L, 11L, 12L), ids);
        verify(repo, never()).findVectorCandidates(anyString(), anyInt());
        verify(repo, never()).findFunTagCandidates(any());
    }

    @Test
    void unionDedupesAcrossSourcesPreservingOrder() {
        when(repo.findVectorCandidates("[0.1]", 300)).thenReturn(List.of(1L, 2L));
        when(repo.findFunTagCandidates(new String[]{"a"})).thenReturn(List.of(2L, 3L));
        when(repo.findQualityFallback(5)).thenReturn(List.of(3L, 4L));
        List<Long> ids = gen.generate("[0.1]", new String[]{"a"}, null, new String[0], 5);
        assertEquals(List.of(1L, 2L, 3L, 4L), ids);
    }
}
