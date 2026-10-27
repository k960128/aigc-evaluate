package com.kant.data.agent.config;

import com.kant.framework.config.RocketMQAutoConfiguration;
import com.kant.framework.mq.producer.DelegatingTransactionListener;
import com.kant.framework.mq.producer.MessageQueueProducer;
import com.kant.framework.mq.producer.RocketMQProducerAdapter;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.transaction.PlatformTransactionManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class RocketMQAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(RocketMQAutoConfiguration.class);

    @Test
    void disabledDoesNotRequireRocketMQOrTransactionManager() {
        contextRunner.withPropertyValues("rocketmq.enable=false").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(MessageQueueProducer.class);
            assertThat(context).doesNotHaveBean(DelegatingTransactionListener.class);
        });
    }

    @Test
    void disabledDoesNotRegisterMessageQueueBeansEvenWhenDependenciesExist() {
        withRocketMQDependencies().withPropertyValues("rocketmq.enable=false").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(MessageQueueProducer.class);
            assertThat(context).doesNotHaveBean(DelegatingTransactionListener.class);
        });
    }

    @Test
    void enabledRegistersMessageQueueBeans() {
        withRocketMQDependencies().withPropertyValues("rocketmq.enable=true").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(DelegatingTransactionListener.class);
            assertThat(context).hasSingleBean(MessageQueueProducer.class);
            assertThat(context.getBean(MessageQueueProducer.class)).isInstanceOf(RocketMQProducerAdapter.class);
        });
    }

    @Test
    void missingSwitchPreservesExistingEnabledBehavior() {
        withRocketMQDependencies().run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(DelegatingTransactionListener.class);
            assertThat(context).hasSingleBean(MessageQueueProducer.class);
        });
    }

    private ApplicationContextRunner withRocketMQDependencies() {
        return contextRunner
                .withBean(RocketMQTemplate.class, () -> mock(RocketMQTemplate.class))
                .withBean(PlatformTransactionManager.class, () -> mock(PlatformTransactionManager.class));
    }
}
