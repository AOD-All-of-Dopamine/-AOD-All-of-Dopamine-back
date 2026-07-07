package com.example.AOD.recommend;

import com.example.AOD.recommend.dto.RecRequest;
import com.example.AOD.recommend.dto.RecommendationItem;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequestMapping("/api/recommendations")
public class RecommendController {

    private final RecommendService recommendService;

    public RecommendController(RecommendService recommendService) {
        this.recommendService = recommendService;
    }

    /** contracts §7: GET /api/recommendations?location=&selectedContentId=&page=&size=.
     *  M2는 홈 서빙 + 익명(userId null) 콜드스타트; principal→userId·related 서빙은 M3. */
    @GetMapping
    public ResponseEntity<List<RecommendationItem>> recommend(
            @RequestParam(defaultValue = "home") String location,
            @RequestParam(required = false) Long selectedContentId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        RecRequest req = new RecRequest(location, selectedContentId, page, size, null);
        return ResponseEntity.ok(recommendService.recommend(req));
    }
}
