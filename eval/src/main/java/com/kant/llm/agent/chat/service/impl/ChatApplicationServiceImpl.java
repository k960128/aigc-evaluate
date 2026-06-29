package com.kant.llm.agent.chat.service.impl;

import com.kant.llm.agent.chat.constant.ChatSource;
import com.kant.llm.agent.chat.service.ChatApplicationService;
import com.kant.llm.agent.chat.service.ChatConversationService;
import com.kant.llm.agent.chat.service.ChatMessageService;
import com.kant.llm.agent.ai.service.TitleSummaryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Slf4j
@Service
public class ChatApplicationServiceImpl implements ChatApplicationService {

    private final ChatMessageService chatMessageService;
    private final TitleSummaryService titleSummaryService;
    private final ChatConversationService chatConversationService;

    public ChatApplicationServiceImpl(ChatConversationService chatConversationService,
                                      ChatMessageService chatMessageService,
                                      TitleSummaryService titleSummaryService) {
        this.chatConversationService = chatConversationService;
        this.chatMessageService = chatMessageService;
        this.titleSummaryService = titleSummaryService;
    }

    /**
     * 统一流式对话入口。
     * <p>
     * 封装原本散落在 {@link ChatSource} 中的完整对话流程：
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

        // 2. 保存用户消息
        String messageId = chatMessageService.saveUserMessage(finalConversationId, content);
        log.info("保存用户消息: messageId={}, conversationId={}, content={}", messageId, finalConversationId, content);

        // 3. 流式返回：先发送意图识别进度，再执行意图识别
        return Flux.just("[PROGRESS]:正在识别您的意图...")
                .concatWith(Flux.concat(
                        Flux.just("[PROGRESS]:正在生成回答..."),
                        Flux.just("balibajiasdofjoewfgenwgojoiejriojgog")
                ))
                .subscribeOn(Schedulers.boundedElastic())
                .doOnError(e ->  log.error("流式对话异常: conversationId={}", finalConversationId, e))
                // 6. 在流末尾追加一条 [DONE] 事件，携带 conversationId
                .concatWith(Mono.just("[DONE]:" + finalConversationId));
    }
}