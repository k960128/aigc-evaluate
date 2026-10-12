package com.kant.springai.agent.dto;

public record GenerateRequest(String topic) {

    public GenerateRequest {
        if (topic == null || topic.isBlank()) {
            topic = "Spring AI 多智能体协作实战";
        }
    }
}