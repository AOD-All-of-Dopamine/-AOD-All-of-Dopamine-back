package com.example.crawler.service;

import com.example.crawler.rules.MappingRule;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;

@Component
public class RuleLoader {
    public MappingRule load(String pathOnClasspath) {
        try (InputStream in = new ClassPathResource(pathOnClasspath).getInputStream()) {
            Yaml yaml = new Yaml();
            return yaml.loadAs(in, MappingRule.class);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load rule: " + pathOnClasspath, e);
        }
    }
}


