package com.kant.llm.agent.ai.service;

import com.kant.llm.agent.ai.constant.EvalIntent;
import com.kant.llm.agent.ai.model.IntentRecognitionResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;
import org.springframework.util.FileCopyUtils;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 提示词服务
 */
@Slf4j
@Service
public class PromptService {

    private final Map<EvalIntent, String> promptCache = new ConcurrentHashMap<>();
    private final PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

    /**
     * 根据意图获取提示词
     */
    public String getPrompt(EvalIntent intent) {
        return promptCache.computeIfAbsent(intent, this::loadPromptFromFile);
    }

    /**
     * 根据意图获取提示词
     */
    public String getPrompt(IntentRecognitionResult intent) {
        return promptCache.computeIfAbsent(EvalIntent.getIntent(intent), this::loadPromptFromFile);
    }

    /**
     * 从文件加载提示词（带缓存）
     */
    private String loadPromptFromFile(EvalIntent intent) {
        try {
            Resource resource = resolver.getResource("classpath:/prompts/" + intent.getFileName());
            return FileCopyUtils.copyToString(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            // 如果指定意图的文件不存在，返回默认提示词
            if (intent != EvalIntent.CAR_OTHER_QUERY) {
                return getPrompt(EvalIntent.CAR_OTHER_QUERY);
            }
            throw new RuntimeException("默认提示词文件缺失", e);
        }
    }
}

