package com.example.AOD.recommend.candidate;

import com.example.AOD.recommend.dto.FunTag;
import com.example.AOD.recommend.dto.QualityScore;
import com.example.AOD.recommend.feature.FeatureVector;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Candidate {
    public Long contentId;
    public String domain;                 // MOVIE/TV/GAME/WEBTOON/WEBNOVEL
    public List<String> platforms = new ArrayList<>();
    public String ageRating;              // nullable (spec §10 이슈5: 없을 수 있음)
    public boolean hidden;
    public boolean unavailable;
    public String candidateSource;        // "vector_ann" | "fun_tag" | "quality"
    public double score;
    public float[] embedding;
    public List<FunTag> funTags = new ArrayList<>();
    public QualityScore quality;
    public Set<String> genres = new HashSet<>();
    public LocalDate releaseDate;
    public FeatureVector features;

    public Candidate(Long contentId, String domain) {
        this.contentId = contentId;
        this.domain = domain;
    }
}
