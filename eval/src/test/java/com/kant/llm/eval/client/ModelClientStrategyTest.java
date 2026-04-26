package com.kant.llm.eval.client;

import com.kant.llm.eval.common.enums.ModelManufacturerEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@SpringBootTest
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class ModelClientStrategyTest {

    private final ModelClientStrategyFactory modelClientStrategyFactory;

    private final String DEEP_SEEK = "sk-b9a7cb64a3e24c109bfe46214d1ee823";
    private final String QWEN_API_KEY = "sk-3398a71a31f04728a19922dcbb8af1f9";

    private Map<ModelManufacturerEnum, ModelInfo> modelInfoMap;

    @Test
    void test() {
        buildModelInfoMap();
        ModelInfo modelInfo = modelInfoMap.get(ModelManufacturerEnum.QWEN);
        ModelRequest request = ModelRequest.builder()
                .modelInfo(modelInfo)
                .inputText("你是什么大模型?")
                .build();
        ModelClientStrategy strategy = modelClientStrategyFactory.getStrategy(modelInfo);
        ModelResponse call = strategy.call(request);
        log.info("call: {}", call);
    }

    private void buildModelInfoMap() {
        modelInfoMap = new ConcurrentHashMap<>() {{
            put(ModelManufacturerEnum.DEEPSEEK, ModelInfo.builder()
                    .apiKey(DEEP_SEEK)
                    .baseUrl("https://api.deepseek.com")
                    .modelId(1L)
                    .model("deepseek-chat")
                    .manufacturerType(ModelManufacturerEnum.DEEPSEEK)
                    .build());

            put(ModelManufacturerEnum.QWEN, ModelInfo.builder()
                    .apiKey(QWEN_API_KEY)
                    .baseUrl("https://dashscope.aliyuncs.com/compatible-mode")
                    .modelId(2L)
                    .model("qwen-plus")
                    .manufacturerType(ModelManufacturerEnum.QWEN)
                    .build());
        }};
    }
}
