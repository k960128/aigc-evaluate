package com.kant.springai.agent.advisor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;

@Slf4j
public class ConsumerCallAdvisor implements CallAdvisor {
    @Override
    public ChatClientResponse adviseCall(ChatClientRequest chatClientRequest,
                                         CallAdvisorChain callAdvisorChain) {
        log.info("==================== ConsumerCallAdvisor ===================");
        ChatClientResponse chatClientResponse = callAdvisorChain.nextCall(chatClientRequest);
        log.info("chatClientRequest:{}", chatClientRequest);
        log.info("adviseCall:{}", chatClientResponse.chatResponse());
        return chatClientResponse;
    }

    @Override
    public String getName() {
        return "ConsumerCallAdvisor";
    }

    @Override
    public int getOrder() {
        return 0;
    }
}
