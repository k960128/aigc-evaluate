package com.kant.springai.agent.tools;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Map;

/**
 * 暴露给「研究员 Agent」的工具集。
 * 真实项目里可以替换成 Tavily / SerpAPI / 内部知识库检索 / Elasticsearch 等。
 */
@Slf4j
@Component
public class ResearchTools {
    private static final Map<String, String> KNOWLEDGE_BASE = Map.of(
            "spring ai", """
                    Spring AI 是 Spring 生态的 AI 应用框架，核心抽象包括：
                    - ChatClient：同步/流式调用大模型的统一入口
                    - ChatModel：底层模型适配（OpenAI、Azure OpenAI、Ollama、Anthropic 等）
                    - Advisor：请求/响应拦截器链，可用于 RAG、对话记忆、日志
                    - Tool Calling：通过 @Tool 注解把 Java 方法暴露给模型
                    - Structured Output：entity(Class) 直接把模型输出映射为 Java 对象
                    1.0.0 起正式 GA，要求 Spring Boot 3.4+ / Java 17+。
                    """,
            "multi-agent", """
                    多智能体（Multi-Agent）常见编排模式：
                    - 顺序流水线 Pipeline：Agent A -> B -> C
                    - 黑板模式 Blackboard：共享上下文，各 Agent 自由读写
                    - 监督者模式 Supervisor：由 LLM 决定下一个执行谁
                    - 辩论/评审模式 Debate：多个 Agent 互相评审、迭代改进
                    工程关键点：上下文隔离、失败重试、轮次上限、可观测性（trace）。
                    """,
            "spring boot", """
                    Spring Boot 3.x 关键特性：Java 17 基线、AOT/GraalVM 原生镜像、
                    Micrometer Observation 可观测性、@HttpExchange 声明式 HTTP 客户端、
                    @ConfigurationProperties 支持 record 绑定。
                    """
    );

    @Tool(description = "搜索技术知识库，输入关键词，返回相关资料摘要")
    public String searchKnowledgeBase(
            @ToolParam(description = "搜索关键词，例如：spring ai") String query) {
        log.info("[Tool] searchKnowledgeBase(query={})", query);
        String key = query == null ? "" : query.toLowerCase().trim();
        return KNOWLEDGE_BASE.entrySet().stream()
                .filter(e -> key.contains(e.getKey()) || e.getKey().contains(key))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(String.format("知识库中没有关于「%s」的精确条目，请基于通用知识作答。", query));
    }

    @Tool(description = "获取今天的日期，用于给文章标注写作时间")
    public String today() {
        return LocalDate.now().toString();
    }
}
