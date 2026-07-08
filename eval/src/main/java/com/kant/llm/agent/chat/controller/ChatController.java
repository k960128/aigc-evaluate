package com.kant.llm.agent.chat.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.kant.llm.agent.chat.constant.ChatSource;
import com.kant.llm.agent.chat.req.ChatSendReq;
import com.kant.llm.agent.chat.service.ChatApplicationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * 流式对话接口。
 *
 * <p>该 Controller 暴露普通聊天入口和安全评测助手入口。
 * 两个入口共用会话与消息基础设施，但后端处理链路不同。</p>
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
     * 普通流式对话入口。
     *
     * <p>该入口沿用现有聊天链路：保存会话消息、识别意图、进入通用 RAG 或通用对话流程。
     * 返回格式为 SSE 文本流，流结束前会追加 {@code [DONE]:conversationId}。</p>
     *
     * @param req 对话请求，包含用户问题和可选会话 ID
     * @return SSE 流式响应
     */
    @PostMapping(value = "/send", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> send(@RequestBody ChatSendReq req) {
        return chatApplicationService.chat(StpUtil.getLoginIdAsString(), req.getContent(), req.getConversationId(), ChatSource.USER_WEB);
    }

    /**
     * 安全评测助手流式对话入口。
     *
     * <p>该入口当前只接入安全评测专用意图识别和 Slot Validator。
     * 当槽位缺失时直接返回澄清问题；当槽位通过时返回意图识别与校验结果。
     * 后续接入 Skill Router 时，可在 {@code ChatApplicationService.evalChat} 中继续向下分发。</p>
     *
     * @param req 安全评测助手请求，包含用户问题和可选会话 ID
     * @return SSE 流式响应
     */
    @PostMapping(value = "/eval/send", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> evalSend(@RequestBody ChatSendReq req) {
        return chatApplicationService.evalChat("2001523723396308993", req.getContent(), req.getConversationId(), ChatSource.USER_WEB);
    }
}
