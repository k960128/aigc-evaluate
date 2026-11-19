package com.kant.springai.agent.advisor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;

/**
 * 测试SpringAI的Advisor
 */
@Slf4j
public class ConsumerBaseAdvisor implements BaseAdvisor {
    @Override
    public String getName() {
        return "ConsumerAdvisor";
    }

    @Override
    public ChatClientRequest before(ChatClientRequest chatClientRequest,
                                    AdvisorChain advisorChain) {
        log.info("======================== Before Consumer Advisor ========================");
        log.info("chatClientRequest:{}", chatClientRequest);
        return chatClientRequest;
    }

    @Override
    public ChatClientResponse after(ChatClientResponse chatClientResponse, AdvisorChain advisorChain) {
        log.info("======================== After Consumer Advisor ========================");
        log.info("chatClientResponse:{}", chatClientResponse);
        return chatClientResponse;
    }

    @Override
    public int getOrder() {
        return 0;
    }
}
