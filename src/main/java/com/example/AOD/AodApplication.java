package com.example.AOD;

import com.example.AOD.domain.entity.Domain;
import com.example.AOD.rules.MappingRule;
import com.example.AOD.service.RuleLoader;
import com.example.AOD.service.TransformEngine;
import com.example.AOD.service.UpsertService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.io.InputStream;
import java.util.Map;

@SpringBootApplication
@EnableAsync
@EnableScheduling // 스케줄링 기능 활성화
public class AodApplication implements CommandLineRunner {
	private final RuleLoader ruleLoader;
	private final TransformEngine transform;
	private final UpsertService upsert;
	private final ObjectMapper om = new ObjectMapper();

	public AodApplication(RuleLoader r, TransformEngine t, UpsertService u){
		this.ruleLoader = r; this.transform = t; this.upsert = u;
	}

	public static void main(String[] args) { SpringApplication.run(AodApplication.class, args); }

	@Override public void run(String... args) throws Exception {
		// 예시 RAW (Steam appdetails 응답 중 data 블록 형태를 약식화)
		try (InputStream in = new ClassPathResource("sample/raw-steam.json").getInputStream()) {
			Map<String,Object> raw = om.readValue(in, Map.class);

			MappingRule rule = ruleLoader.load("rules/game/steam.yml");
			var tri = transform.transform(raw, rule);

			String platformSpecificId = String.valueOf(
					transform.deepGet(raw, "appId") != null ? transform.deepGet(raw, "appId") :
							transform.deepGet(raw, "data.steam_appid")
			);
			String url = "https://store.steampowered.com/app/" + platformSpecificId;

			Long contentId = upsert.upsert(
					Domain.valueOf(rule.getDomain()),
					tri.master(), tri.platform(), tri.domain(),
					platformSpecificId, url
			);
			System.out.println("[GAME/Steam] UPSERT OK content_id=" + contentId);
		}
	}
}