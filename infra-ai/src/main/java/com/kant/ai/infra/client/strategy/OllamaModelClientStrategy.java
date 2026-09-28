package com.kant.ai.infra.client.strategy;

import cn.hutool.core.util.ObjectUtil;
import com.kant.ai.infra.client.ModelClientStrategy;
import com.kant.ai.infra.client.ModelConnectionResponse;
import com.kant.ai.infra.client.ModelRequest;
import com.kant.ai.infra.client.ModelResponse;
import com.kant.ai.infra.enums.ModelManufacturerEnum;
import io.micrometer.observation.ObservationRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;

@Slf4j
public class OllamaModelClientStrategy implements ModelClientStrategy {

    private static final String CONNECTION_TEST_PROMPT = "只返回1，不要返回其他任何内容！";
    private final ObservationRegistry observationRegistry;

    public OllamaModelClientStrategy(ObservationRegistry observationRegistry) {
        this.observationRegistry = observationRegistry;
    }

    @Override
    public ModelResponse call(ModelRequest modelRequest) {
        long startTime = System.currentTimeMillis();
        ChatClient.CallResponseSpec response = createChatClient(modelRequest)
                .prompt(modelRequest.getInputText())
                .call();
        String content = response.content();
        long endTime = System.currentTimeMillis();
        log.info("OllamaModelClientStrategy response: {}", content);
        return ModelResponse.builder()
                .modelId(modelRequest.getClientModelInfo().getModelId())
                .respContent(content)
                .elapsed(endTime - startTime)
                .build();
    }

    @Override
    public ModelManufacturerEnum getManufacturer() {
        return ModelManufacturerEnum.OLLAMA;
    }

    @Override
    public ModelConnectionResponse connection(ModelRequest modelRequest) {
        long startTime = System.currentTimeMillis();
        String content = createChatClient(modelRequest)
                .prompt(CONNECTION_TEST_PROMPT)
                .call()
                .content();
        long endTime = System.currentTimeMillis();
        log.info("OllamaModelClientStrategy connection response: {}", content);
        return ModelConnectionResponse.builder()
                .result(format(content))
                .respContent(content)
                .elapsed(endTime - startTime)
                .build();
    }

    private ChatClient createChatClient(ModelRequest modelRequest) {
        OllamaApi ollamaApi = OllamaApi.builder()
                .baseUrl(modelRequest.getClientModelInfo().getBaseUrl())
                .build();
        OllamaChatModel chatModel = OllamaChatModel.builder()
                .ollamaApi(ollamaApi)
                .defaultOptions(OllamaChatOptions.builder()
                        .model(modelRequest.getClientModelInfo().getModel())
                        .disableThinking()
                        .build())
                .observationRegistry(observationRegistry)
                .build();
        return ChatClient.create(chatModel, observationRegistry);
    }

    /**
     * 格式化
     */
    private Boolean format(String content) {
        // 剔除内容中包含的\n
        content = content.replaceAll("\\n", "");
        return ObjectUtil.equal(content, "1");
    }
}
