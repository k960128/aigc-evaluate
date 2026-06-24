package com.kant.llm.agent.service;

import com.kant.llm.agent.constant.ChatSource;
import reactor.core.publisher.Flux;

public interface ChatApplicationService {

    Flux<String> chat(String userId, String content, String conversationId, ChatSource chatSource);
}
