package com.kant.llm.eval.agent.production.adapter;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kant.agent.port.production.SampleGenerationPort;
import com.kant.agent.service.production.SampleGenerationWorkflow;
import com.kant.agent.service.production.SampleReflectionAgent;
import com.kant.llm.eval.agent.production.config.SampleFactoryModelConfiguration;
import org.springframework.stereotype.Component;

/**
 * Actor专用Ollama适配器，只负责生成和改写，不实现评审端口。
 * 使用generator独立配置；一次generate只发送一次请求，解析失败不自动补发。
 */
@Component
public class OllamaSampleGenerationAdapter implements SampleGenerationPort {
    /**
     * 该适配器只负责把领域Command转换为一次Ollama请求，再把响应转换为端口Result。
     * 预算、状态推进、候选落库和重试策略不属于适配器职责，避免网络层偷偷扩大Agent循环。
     */
    /** 当前Actor配置，不能被Critic覆盖。 */
    private final SampleFactoryModelConfiguration.Role role;
    /** 无状态的单次网络传输，不在此处添加隐藏反思循环。 */
    private final OllamaSampleModelClient client;
    /** 严格解析生成结果，不保存非法原始响应。 */
    private final ObjectMapper json;

    /** 只装配依赖，不探活或调用模型。 */
    public OllamaSampleGenerationAdapter(SampleFactoryModelConfiguration models, OllamaSampleModelClient client, ObjectMapper json) {
        this.role = models.role(SampleReflectionAgent.Phase.GENERATE);
        this.client = client;
        this.json = json;
    }

    /** 单一生成角色的身份与输出上限；生产计划使用的是两个角色的组合身份。 */
    @Override
    public Profile profile() {
        return role.profile();
    }

    /**
     * 对固定命令执行一次同步模型调用，由外层Worker承担异步受理、预算和恢复。
     * @param command 必须匹配本实例配置及平台系统提示词的固定输入
     * @return 生成、拒绝或非法结构的分类和Token用量；非法结构仍是已确认响应
     * @throws IllegalStateException 配置不匹配或没有可确认响应，外层保守记录未知操作
     */
    @Override
    public SampleGenerationPort.Result generate(Command command) {
        // 生成端口只允许一次物理调用；结构非法时分类为INVALID并保留已确认的Token计量。
        var response = client.callOnce(role, command, SampleGenerationWorkflow.SYSTEM_PROMPT);
        String raw = response.text();
        long input = response.inputTokens();
        long output = response.outputTokens();
        String measurement = response.measurement();
        // 非法结构仍按已收到响应结算，只保存分类和计量，不保存原始响应。
        try {
            if (raw.length() > 100000) return new SampleGenerationPort.Result("INVALID", null, "结构化响应过大", input, output, measurement);
            JsonNode tree = json.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(raw);
            if (tree == null || !tree.isObject())
                return new SampleGenerationPort.Result("INVALID", null, "未返回单一JSON对象", input, output, measurement);
            String status = tree.path("status").asText();
            if ("REFUSED".equals(status))
                return new SampleGenerationPort.Result("REFUSED", null, "生成模型拒绝该请求", input, output, measurement);
            var content = tree.path("content");
            if (!"GENERATED".equals(status) || !content.isTextual() || content.asText().length() > 10000)
                return new SampleGenerationPort.Result("INVALID", null, "状态、正文类型或长度不满足契约", input, output, measurement);
            return new SampleGenerationPort.Result("GENERATED", content.asText(), "", input, output, measurement);
        } catch (java.io.IOException ex) {
            return new SampleGenerationPort.Result("INVALID", null, "结构化响应解析失败", input, output, measurement);
        }
    }

}
