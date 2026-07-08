package com.kant.llm.agent.ai.service;

import com.kant.llm.agent.ai.model.IntentRecognitionResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

/**
 * 意图识别服务
 */
@Slf4j
@Service
public class IntentRecognitionService {

    @Value("classpath:/prompts/security-eval-intent-recognition-prompt.txt")
    private Resource recognitionPrompt;

    private final ChatClient plainChatClient;

    public IntentRecognitionService(@Qualifier("plainChatClient") ChatClient plainChatClient) {
        this.plainChatClient = plainChatClient;
    }

    public IntentRecognitionResult chat(String query) {
        return plainChatClient
                .prompt()
                .system(recognitionPrompt)
                .user(u -> u.text(query))
                .call().entity(IntentRecognitionResult.class);
    }

}
