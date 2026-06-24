package com.kant.llm.agent.service.impl;

import com.kant.llm.agent.constant.ChatSource;
import com.kant.llm.agent.service.ChatApplicationService;
import com.kant.llm.agent.service.ChatConversationService;
import com.kant.llm.agent.tools.TitleSummaryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.Map;

@Slf4j
@Service
public class ChatApplicationServiceImpl implements ChatApplicationService {

    private final ChatConversationService chatConversationService;
    private final TitleSummaryService titleSummaryService;

    public ChatApplicationServiceImpl(ChatConversationService chatConversationService,
                                      TitleSummaryService titleSummaryService) {
        this.chatConversationService = chatConversationService;
        this.titleSummaryService = titleSummaryService;
    }

    /**
     * 统一流式对话入口。
     * <p>
     * 封装原本散落在 {@link com.kant.llm.agent.constant.ChatSource} 中的完整对话流程：
     * 会话创建（可选）、异步标题生成、保存用户/助手消息、意图识别、不相关问题兜底通用对话、
     * 相关问题 RAG 对话。HTTP 接口与钉钉机器人回调均通过此方法复用同一条对话链路。
     *
     * @param userId         用户ID
     * @param content        用户问题
     * @param conversationId 会话ID（可选，为空则自动创建新会话）
     * @return 包含进度消息、[DONE] 事件及 LLM token 的 SSE 流
     */
    @Override
    public Flux<String> chat(String userId,
                             String content,
                             String conversationId,
                             ChatSource chatSource) {

        // 1. 如果conversationId为空，创建新会话
        final String finalConversationId;
        if (conversationId == null || conversationId.isBlank()) {
            String tempTitle = content.substring(0, Math.min(content.length(), 20));
            finalConversationId = chatConversationService.createConversation(userId, tempTitle);
            log.info("创建新会话: conversationId={}, tempTitle={}", finalConversationId, tempTitle);

            // 异步：用虚拟线程调用 LLM 生成摘要标题，完成后回写到数据库
            Thread.ofVirtual().name("title-summary-" + finalConversationId).start(() -> {
                try {
                    String aiTitle = titleSummaryService.generateTitle(content);
                    Boolean respFlag = chatConversationService.updateTitle(finalConversationId, aiTitle);
                    log.info("异步标题更新完成: respFlag={}, conversationId={}, title={}", respFlag, finalConversationId, aiTitle);
                } catch (Exception e) {
                    log.warn("异步标题生成失败, 保留临时标题: conversationId={}", finalConversationId, e);
                }
            });

        } else {
            finalConversationId = conversationId;
        }

        return null;
    }
}