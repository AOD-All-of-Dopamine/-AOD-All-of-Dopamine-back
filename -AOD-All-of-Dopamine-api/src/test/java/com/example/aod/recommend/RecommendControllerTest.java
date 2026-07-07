package com.example.AOD.recommend;

import com.example.AOD.recommend.dto.RecRequest;
import com.example.AOD.recommend.dto.RecommendationItem;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.List;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RecommendControllerTest {

    @Test
    void homeReturnsRankedItems() throws Exception {
        RecommendService service = mock(RecommendService.class);
        when(service.recommend(any(RecRequest.class))).thenReturn(List.of(
                new RecommendationItem(1L, "MOVIE", 0.9, "quality", 0, "{}")));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new RecommendController(service)).build();

        mvc.perform(get("/api/recommendations").param("location", "home"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].contentId").value(1))
                .andExpect(jsonPath("$[0].candidateSource").value("quality"))
                .andExpect(jsonPath("$[0].rankPosition").value(0));

        verify(service).recommend(argThat(r ->
                r.location().equals("home") && r.size() == 20 && r.userId() == null));
    }
}
