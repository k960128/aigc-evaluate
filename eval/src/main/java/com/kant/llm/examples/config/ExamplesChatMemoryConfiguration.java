package com.kant.llm.examples.config;

import com.kant.llm.examples.memory.ExamplesDataBaseChatMemoryRepository;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ExamplesChatMemoryConfiguration {

    @Bean(name = "examplesChatMemoryRepository")
    ChatMemoryRepository chatMemoryRepository() {
        return new InMemoryChatMemoryRepository();
    }

    @Bean(name = "examplesChatMemory")
    public ChatMemory chatMemory(@Qualifier("examplesChatMemoryRepository") ChatMemoryRepository chatMemoryRepository) {
        return MessageWindowChatMemory.builder()
                .maxMessages(10)
                .chatMemoryRepository(chatMemoryRepository)
                .build();
    }

    @Bean(name = "examplesDataBaseChatMemory")
    public ChatMemory chatDataBaseMemory(ExamplesDataBaseChatMemoryRepository examplesChatMemoryRepository) {
        return MessageWindowChatMemory.builder()
                .maxMessages(10)
                .chatMemoryRepository(examplesChatMemoryRepository)
                .build();
    }
}
