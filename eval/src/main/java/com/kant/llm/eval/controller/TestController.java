package com.kant.llm.eval.controller;

import com.kant.llm.eval.client.*;
import com.kant.llm.eval.common.convention.Result;
import com.kant.llm.eval.common.enums.ModelManufacturerEnum;
import com.kant.llm.eval.common.web.Results;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/test")
public class TestController {

    private final String DEEP_SEEK = "sk-b9a7cb64a3e24c109bfe46214d1ee823";
    private final String QWEN_API_KEY = "sk-3398a71a31f04728a19922dcbb8af1f9";
    private final String GLM_API_KEY = "sk-of-quYqxutarrXLSygXRYAGvGawphLlOpibxDOGDglJROtTDFWVLAYGIHKRFesvRkCd";

    private Map<ModelManufacturerEnum, ModelInfo> modelInfoMap;

    private final ModelClientStrategyFactory modelClientStrategyFactory;

    public TestController(ModelClientStrategyFactory modelClientStrategyFactory) {
        this.modelClientStrategyFactory = modelClientStrategyFactory;
        this.modelInfoMap = new ConcurrentHashMap<>() {{
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

            put(ModelManufacturerEnum.GLM, ModelInfo.builder()
                    .apiKey(GLM_API_KEY)
                    .baseUrl("https://api.ofox.ai/v1/chat/completions")
                    .modelId(3L)
                    .model("z-ai/glm-4.7-flash:free")
                    .manufacturerType(ModelManufacturerEnum.GLM)
                    .build());
        }};
    }

    /**
     * 测试模型客户端策略
     * @return 模型厂商名称
     */
    @GetMapping("/testModelClient")
    public Result<String> testModelClientStrategy() {
        ModelInfo modelInfo = new ModelInfo();
        modelInfo.setModelId(1L);
        modelInfo.setManufacturerType(ModelManufacturerEnum.DEEPSEEK);
        ModelClientStrategy strategy = modelClientStrategyFactory.getStrategy(modelInfo);
        return Results.success(strategy.getManufacturer().name());
    }
}
