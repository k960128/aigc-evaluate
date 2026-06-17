package com.kant.ai.infra.config;

import com.kant.ai.infra.client.ModelClientStrategyFactory;
import com.kant.ai.infra.embedding.EmbeddingModelInfoProvider;
import com.kant.ai.infra.embedding.EmbeddingModelInitializer;
import com.kant.ai.infra.embedding.EmbeddingModelRegistry;
import com.kant.ai.infra.embedding.EmbeddingModelStrategy;
import com.kant.ai.infra.embedding.EmbeddingModelStrategyFactory;
import com.kant.ai.infra.embedding.EmbeddingService;
import com.kant.ai.infra.embedding.RoutingEmbeddingService;
import com.kant.ai.infra.embedding.strategy.QwenEmbeddingModelStrategy;
import com.kant.ai.infra.judge.JudgeModelClient;
import com.kant.ai.infra.judge.JudgeModelStrategy;
import com.kant.ai.infra.judge.JudgeModelStrategyFactory;
import com.kant.ai.infra.judge.strategy.QwenJudgeModelStrategy;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;

import java.util.List;

@AutoConfiguration
public class InfraAiConfiguration {
    @Bean
    public ModelClientStrategyFactory buildModelClientStrategyFactory() {
        return new ModelClientStrategyFactory();
    }

    @Bean
    public QwenJudgeModelStrategy qwenJudgeModelStrategy() {
        return new QwenJudgeModelStrategy();
    }

    @Bean
    public JudgeModelStrategyFactory judgeModelStrategyFactory(List<JudgeModelStrategy> judgeModelStrategies) {
        return new JudgeModelStrategyFactory(judgeModelStrategies);
    }

    @Bean
    public JudgeModelClient judgeModelClient(JudgeModelStrategyFactory judgeModelStrategyFactory) {
        return new JudgeModelClient(judgeModelStrategyFactory);
    }

    @Bean
    public QwenEmbeddingModelStrategy qwenEmbeddingModelStrategy() {
        return new QwenEmbeddingModelStrategy();
    }

    @Bean
    public EmbeddingModelStrategyFactory embeddingModelStrategyFactory(List<EmbeddingModelStrategy> embeddingModelStrategies) {
        return new EmbeddingModelStrategyFactory(embeddingModelStrategies);
    }

    @Bean
    public EmbeddingModelRegistry embeddingModelRegistry(EmbeddingModelStrategyFactory embeddingModelStrategyFactory,
                                                         ObjectProvider<EmbeddingModelInfoProvider> embeddingModelInfoProvider) {
        return new EmbeddingModelRegistry(embeddingModelStrategyFactory, embeddingModelInfoProvider);
    }

    @Bean
    public EmbeddingModelInitializer embeddingModelInitializer(EmbeddingModelRegistry embeddingModelRegistry) {
        return new EmbeddingModelInitializer(embeddingModelRegistry);
    }

    @Bean
    public EmbeddingService embeddingService(EmbeddingModelRegistry embeddingModelRegistry) {
        return new RoutingEmbeddingService(embeddingModelRegistry);
    }
}
