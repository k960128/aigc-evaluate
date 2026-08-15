package com.kant.llm.agent.chat.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.kant.framework.convention.Result;
import com.kant.framework.web.Results;
import com.kant.llm.agent.chat.dao.entity.ChatConversationDO;
import com.kant.llm.agent.chat.service.ChatConversationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/**
 * AI对话会话 Controller
 */
@Slf4j
@RestController
@RequestMapping("/agent/chat/conversation")
public class ChatConversationController {

    private final ChatConversationService chatConversationService;

    public ChatConversationController(ChatConversationService chatConversationService) {
        this.chatConversationService = chatConversationService;
    }

    /**
     * 获取用户的会话列表
     ** @return 会话列表
     */
    @GetMapping("/list")
    public Result<List<ChatConversationDO>> getConversationList() {
        List<ChatConversationDO> conversations = chatConversationService.getConversationsByUserId(StpUtil.getLoginIdAsString());
        return Results.success(conversations);
    }

    /**
     * 根据会话ID获取会话详情
     *
     * @param conversationId 会话ID
     * @return 会话信息
     */
    @GetMapping("/detail")
    public Result<ChatConversationDO> getConversationDetail(@RequestParam String conversationId) {
        ChatConversationDO conversation = chatConversationService.getByConversationId(conversationId);
        return Results.success(conversation);
    }

    /**
     * 创建新会话
     * @param title  会话标题（可选）
     * @return 会话ID
     */
    @PostMapping("/create")
    public Result<String> createConversation(@RequestParam(required = false) String title) {
        String conversationId = chatConversationService.createConversation(StpUtil.getLoginIdAsString(), title);
        return Results.success(conversationId);
    }

    /**
     * 归档会话
     *
     * @param conversationId 会话ID
     * @return 操作结果
     */
    @PutMapping("/archive")
    public Result<Boolean> archiveConversation(@RequestParam String conversationId) {
        return Results.success(chatConversationService.archiveConversation(conversationId));
    }

    /**
     * 删除会话
     *
     * @param conversationId 会话ID
     * @return 操作结果
     */
    @DeleteMapping("/delete")
    public Result<Boolean> deleteConversation(@RequestParam String conversationId) {
        return Results.success(chatConversationService.deleteConversation(conversationId));
    }
}
