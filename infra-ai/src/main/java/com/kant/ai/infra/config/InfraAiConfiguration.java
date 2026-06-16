package com.kant.ai.infra.config;

import com.kant.ai.infra.client.ModelClientStrategyFactory;
import com.kant.ai.infra.embedding.EmbeddingService;
import com.kant.ai.infra.embedding.RoutingEmbeddingService;
import com.kant.ai.infra.judge.JudgeModelClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
public class InfraAiConfiguration {
    @Bean
    public ModelClientStrategyFactory buildModelClientStrategyFactory() {
        return new ModelClientStrategyFactory();
    }

    @Bean
    public JudgeModelClient judgeModelClient() {
        return new JudgeModelClient();
    }

    @Bean
    public EmbeddingService  embeddingService() {
        return new RoutingEmbeddingService();
    }
}
