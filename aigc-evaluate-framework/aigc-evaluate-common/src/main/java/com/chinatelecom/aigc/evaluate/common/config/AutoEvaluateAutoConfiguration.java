package com.chinatelecom.aigc.evaluate.common.config;

import com.chinatelecom.aigc.evaluate.common.enums.JudgeResultEnum;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "auto-evaluate")
public class AutoEvaluateAutoConfiguration {
    private boolean enable;
    private boolean autoToManual;

    private String url;

    private String path;

    private String model;

    private String description;

    private String apiKey;

    private final KeywordConfig judgeKeywords = new KeywordConfig();


    @Getter
    @Setter
    public static class KeywordConfig {
        private Map<JudgeResultEnum, String[]> forwardMap = new LinkedHashMap<>();
        private Map<JudgeResultEnum, String[]> negativeMap = new LinkedHashMap<>();
        private Map<JudgeResultEnum, String[]> forwardAnsweredMap = new LinkedHashMap<>();
        private Map<JudgeResultEnum, String[]> negativeAnsweredMap = new LinkedHashMap<>();
    }

}
