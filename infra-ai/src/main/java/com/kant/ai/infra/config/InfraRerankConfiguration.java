package com.kant.ai.infra.config;

import com.alibaba.cloud.ai.dashscope.rerank.DashScopeRerankModel;
import com.kant.ai.infra.rerank.DefaultRerankModelInfoProvider;
import com.kant.ai.infra.rerank.RerankModelInfoProvider;
import com.kant.ai.infra.rerank.RerankModelInitializer;
import com.kant.ai.infra.rerank.RerankModelRegistry;
import com.kant.ai.infra.rerank.RerankModelStrategy;
import com.kant.ai.infra.rerank.RerankModelStrategyFactory;
import com.kant.ai.infra.rerank.RerankService;
import com.kant.ai.infra.rerank.RoutingRerankService;
import com.kant.ai.infra.rerank.strategy.DashScopeRerankModelStrategy;
import com.kant.framework.exception.ServiceException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import java.util.List;

/**
 * infra-ai 通用 Rerank 自动配置。
 *
 * <p>这里只注册策略、注册表和兼容 Bean，不在配置类中写死具体厂商调用细节。
 * 默认模型会在启动初始化器中构建本地客户端对象，但不会主动请求远程 Rerank API。</p>
 */
@AutoConfiguration
public class InfraRerankConfiguration {

    @Bean
    public DashScopeRerankModelStrategy dashScopeRerankModelStrategy() {
        return new DashScopeRerankModelStrategy();
    }

    @Bean
    public RerankModelStrategyFactory rerankModelStrategyFactory(List<RerankModelStrategy> rerankModelStrategies) {
        return new RerankModelStrategyFactory(rerankModelStrategies);
    }

    @Bean
    @ConditionalOnMissingBean(RerankModelInfoProvider.class)
    public RerankModelInfoProvider defaultRerankModelInfoProvider() {
        return new DefaultRerankModelInfoProvider();
    }

    @Bean
    public RerankModelRegistry rerankModelRegistry(RerankModelStrategyFactory rerankModelStrategyFactory,
                                                   ObjectProvider<RerankModelInfoProvider> rerankModelInfoProvider) {
        return new RerankModelRegistry(rerankModelStrategyFactory, rerankModelInfoProvider);
    }

    @Bean
    public RerankModelInitializer rerankModelInitializer(RerankModelRegistry rerankModelRegistry) {
        return new RerankModelInitializer(rerankModelRegistry);
    }

    @Bean(name = "rerankModel")
    public DashScopeRerankModel rerankModel(RerankModelRegistry rerankModelRegistry) {
        Object defaultRerankModel = rerankModelRegistry.getDefaultRerankModel();
        if (defaultRerankModel instanceof DashScopeRerankModel dashScopeRerankModel) {
            return dashScopeRerankModel;
        }
        throw new ServiceException("默认 Rerank 模型不是 DashScopeRerankModel，无法兼容旧注入点");
    }

    @Bean
    public RerankService rerankService(RerankModelRegistry rerankModelRegistry) {
        return new RoutingRerankService(rerankModelRegistry);
    }
}
