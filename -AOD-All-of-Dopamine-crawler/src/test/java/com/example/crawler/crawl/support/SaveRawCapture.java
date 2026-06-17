package com.example.crawler.crawl.support;

import com.example.crawler.ingest.CollectorService;
import org.mockito.ArgumentCaptor;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.mockito.Mockito.verify;

/**
 * Captures the arguments of a single CollectorService.saveRaw(...) call made on a Mockito mock,
 * and renders them as canonical JSON for golden-master assertions.
 */
public final class SaveRawCapture {

    public final String platformName;
    public final String domain;
    public final Map<String, Object> payload;
    public final String platformSpecificId;
    public final String url;

    private SaveRawCapture(String platformName, String domain, Map<String, Object> payload,
                           String platformSpecificId, String url) {
        this.platformName = platformName;
        this.domain = domain;
        this.payload = payload;
        this.platformSpecificId = platformSpecificId;
        this.url = url;
    }

    @SuppressWarnings("unchecked")
    public static SaveRawCapture from(CollectorService mockCollector) {
        ArgumentCaptor<String> platform = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> domain = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map> payload = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<String> psid = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> url = ArgumentCaptor.forClass(String.class);

        verify(mockCollector).saveRaw(
                platform.capture(), domain.capture(), payload.capture(), psid.capture(), url.capture());

        return new SaveRawCapture(
                platform.getValue(), domain.getValue(),
                (Map<String, Object>) payload.getValue(),
                psid.getValue(), url.getValue());
    }

    public String toCanonicalJson() {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("platformName", platformName);
        record.put("domain", domain);
        record.put("platformSpecificId", platformSpecificId);
        record.put("url", url);
        record.put("payload", payload);
        return CanonicalJson.serialize(record);
    }
}
