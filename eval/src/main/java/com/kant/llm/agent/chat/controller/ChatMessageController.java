package com.kant.llm.agent.chat.controller;

import com.kant.framework.convention.Result;
import com.kant.framework.web.Results;
import com.kant.llm.agent.chat.dao.entity.ChatMessageDO;
import com.kant.llm.agent.chat.service.ChatMessageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * AI对话消息 Controller
 */
@Slf4j
@RestController
@RequestMapping("/agent/chat/message")
public class ChatMessageController {

    private final ChatMessageService chatMessageService;

    public ChatMessageController(ChatMessageService chatMessageService) {
        this.chatMessageService = chatMessageService;
    }

    /**
     * 获取会话的消息列表
     *
     * @param conversationId 会话ID
     * @return 消息列表
     */
    @GetMapping("/list")
    public Result<List<ChatMessageDO>> getMessageList(@RequestParam String conversationId) {
        List<ChatMessageDO> messages = chatMessageService.getMessagesByConversationId(conversationId);
        return Results.success(messages);
    }

    /**
     * 根据消息ID获取消息详情
     *
     * @param messageId 消息ID
     * @return 消息信息
     */
    @GetMapping("/detail")
    public Result<ChatMessageDO> getMessageDetail(@RequestParam String messageId) {
        ChatMessageDO message = chatMessageService.getByMessageId(messageId);
        return Results.success(message);
    }

    /**
     * 保存用户消息
     *
     * @param conversationId 会话ID
     * @param content        消息内容
     * @return 消息ID
     */
    @PostMapping("/saveUser")
    public Result<String> saveUserMessage(@RequestParam String conversationId,
                                          @RequestParam String content) {
        String messageId = chatMessageService.saveUserMessage(conversationId, content);
        return Results.success(messageId);
    }

    /**
     * 获取最近N条消息
     *
     * @param conversationId 会话ID
     * @param limit          消息数量
     * @return 消息列表
     */
    @GetMapping("/recent")
    public Result<List<ChatMessageDO>> getRecentMessages(@RequestParam String conversationId,
                                                         @RequestParam(defaultValue = "10") int limit) {
        List<ChatMessageDO> messages = chatMessageService.getRecentMessages(conversationId, limit);
        return Results.success(messages);
    }

    /**
     * 删除会话的所有消息
     *
     * @param conversationId 会话ID
     * @return 操作结果
     */
    @DeleteMapping("/deleteByConversation")
    public Result<Boolean> deleteMessagesByConversation(@RequestParam String conversationId) {
        Boolean success = chatMessageService.deleteMessagesByConversationId(conversationId);
        return Results.success(success);
    }
}
