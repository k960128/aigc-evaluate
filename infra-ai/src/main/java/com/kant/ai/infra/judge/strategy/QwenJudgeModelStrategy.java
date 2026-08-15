package com.kant.ai.infra.judge.strategy;

import cn.hutool.core.util.ObjectUtil;
import com.kant.ai.infra.client.ClientModelInfo;
import com.kant.ai.infra.client.ModelConnectionResponse;
import com.kant.ai.infra.enums.ModelManufacturerEnum;
import com.kant.ai.infra.judge.JudgeModelRequest;
import com.kant.ai.infra.judge.JudgeModelResponse;
import com.kant.ai.infra.judge.JudgeModelStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;

/**
 * Qwen 裁判模型策略。
 *
 * <p>DashScope 提供 OpenAI compatible 协议，因此这里复用 Spring AI OpenAiChatModel 完成调用。</p>
 */
@Slf4j
public class QwenJudgeModelStrategy implements JudgeModelStrategy {

    @Override
    public JudgeModelResponse judge(JudgeModelRequest request) {
        long startTime = System.currentTimeMillis();
        ClientModelInfo modelInfo = request.getClientModelInfo();
        ChatClient client = buildChatClient(modelInfo);
        log.info("QwenJudgeModelStrategy 开始调用裁判模型，targetRiskDetailsId: {}, model: {}",
                request.getTargetRiskDetailsId(), modelInfo.getModel());
        String rawContent = client.prompt(request.getPrompt()).call().content();
        long endTime = System.currentTimeMillis();
        log.info("QwenJudgeModelStrategy 裁判模型调用完成，targetRiskDetailsId: {}, elapsed: {}ms",
                request.getTargetRiskDetailsId(), endTime - startTime);
        return JudgeModelResponse.builder()
                .rawContent(rawContent)
                .elapsed(endTime - startTime)
                .manufacturer(getManufacturer())
                .model(modelInfo.getModel())
                .build();
    }

    @Override
    public ModelConnectionResponse connection(JudgeModelRequest request) {
        long startTime = System.currentTimeMillis();
        ChatClient client = buildChatClient(request.getClientModelInfo());
        String content = client.prompt("只返回1，不要返回其他任何内容！").call().content();
        long endTime = System.currentTimeMillis();
        return ModelConnectionResponse.builder()
                .result(ObjectUtil.equal(content, "1"))
                .respContent(content)
                .elapsed(endTime - startTime)
                .build();
    }

    @Override
    public ModelManufacturerEnum getManufacturer() {
        return ModelManufacturerEnum.QWEN;
    }

    private ChatClient buildChatClient(ClientModelInfo modelInfo) {
        ChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(OpenAiApi.builder()
                        .baseUrl(modelInfo.getBaseUrl())
                        .apiKey(modelInfo.getApiKey())
                        .build())
                .defaultOptions(OpenAiChatOptions.builder()
                        .model(modelInfo.getModel())
                        .build())
                .build();
        return ChatClient.builder(chatModel).build();
    }
}
