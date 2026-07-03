package com.kant.llm.agent.chat.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.kant.llm.agent.chat.constant.ChatSource;
import com.kant.llm.agent.chat.req.ChatSendReq;
import com.kant.llm.agent.chat.service.ChatApplicationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

/**
 * 流式对话接口
 */
@Slf4j
@RestController
@RequestMapping("/agent/chat")
public class ChatController {
    private final ChatApplicationService chatApplicationService;

    public ChatController(ChatApplicationService chatApplicationService) {
        this.chatApplicationService = chatApplicationService;
    }

    /**
     * 流式对话接口
     * <p>
     * 入参content（用户问题）、conversationId（可选）
     * 返回：SSE 流，每个 token 逐字推送；流结束前推送一条 [DONE] 事件携带 conversationId
     * <p>
     * 进度通知格式：{@code [PROGRESS]:xxx...}，用于在前端展示当前处理阶段，减少等待焦虑。
     * 推送环节包括：意图识别、问题改写、问题路由、排序筛选、生成回答等。
     */
    @PostMapping(value = "/send", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> send(@RequestBody ChatSendReq req) {
        return chatApplicationService.chat(StpUtil.getLoginIdAsString(), req.getContent(), req.getConversationId(), ChatSource.USER_WEB);
    }

}
