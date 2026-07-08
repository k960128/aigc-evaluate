package com.kant.llm.agent.ai.service;

import com.kant.llm.agent.ai.model.IntentRecognitionResult;
import com.kant.llm.agent.ai.model.SecurityEvalIntentRecognitionResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

/**
 * 意图识别服务。
 *
 * <p>该服务统一封装基于大模型的结构化意图识别调用。
 * 当前同时保留通用聊天入口的意图识别方法，以及安全评测助手专用的意图识别方法。</p>
 */
@Slf4j
@Service
public class IntentRecognitionService {

    /**
     * 通用聊天入口使用的意图识别 Prompt。
     *
     * <p>当前项目中该入口仍被 {@code ChatApplicationServiceImpl.chat} 使用，
     * 后续如果通用聊天和安全评测聊天完全拆分，可以再替换为各自独立的 Prompt。</p>
     */
    @Value("classpath:/prompts/security-eval-intent-recognition-prompt.txt")
    private Resource recognitionPrompt;

    /**
     * 安全评测助手专用意图识别 Prompt。
     *
     * <p>该 Prompt 负责识别安全评测助手的 10 类意图，并尽量抽取后续 Slot Validator 所需槽位。</p>
     */
    @Value("classpath:/prompts/security-eval-intent-recognition-prompt.txt")
    private Resource securityEvalRecognitionPrompt;

    private final ChatClient plainChatClient;

    public IntentRecognitionService(@Qualifier("plainChatClient") ChatClient plainChatClient) {
        this.plainChatClient = plainChatClient;
    }

    /**
     * 通用聊天入口的意图识别方法。
     *
     * <p>该方法返回旧的 {@link IntentRecognitionResult} 类型，用于兼容现有 {@code chat(...)} 对话链路。</p>
     *
     * @param query 用户原始输入
     * @return 通用意图识别结构化结果
     */
    public IntentRecognitionResult chat(String query) {
        return plainChatClient
                .prompt()
                .system(recognitionPrompt)
                .user(u -> u.text(query))
                .call().entity(IntentRecognitionResult.class);
    }

    /**
     * 安全评测助手专用意图识别方法。
     *
     * <p>该方法使用安全评测统一意图识别 Prompt，并将模型输出直接映射为
     * {@link SecurityEvalIntentRecognitionResult}。调用方拿到结果后，必须继续经过
     * {@code SecurityEvalSlotValidator} 做后端确定性槽位校验。</p>
     *
     * @param query 用户在安全评测助手中的原始输入
     * @return 安全评测意图识别结构化结果
     */
    public SecurityEvalIntentRecognitionResult securityEvalChat(String query) {
        return plainChatClient
                .prompt()
                .system(securityEvalRecognitionPrompt)
                .user(u -> u.text(query))
                .call().entity(SecurityEvalIntentRecognitionResult.class);
    }
}
