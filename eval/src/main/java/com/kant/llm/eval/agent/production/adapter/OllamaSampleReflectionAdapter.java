package com.kant.llm.eval.agent.production.adapter;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kant.agent.port.production.SampleGenerationPort;
import com.kant.agent.port.production.SampleReflectionPort;
import com.kant.agent.service.production.SampleReflectionAgent;
import com.kant.llm.eval.agent.production.config.SampleFactoryModelConfiguration;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Critic专用Ollama适配器，只评价候选质量，不生成候选、不判断目标安全、不批准资产。
 * 使用critic独立配置；允许与Actor选择相同物理模型，但提示词、角色身份和预算预留互相独立。
 */
@Component
public class OllamaSampleReflectionAdapter implements SampleReflectionPort {
    /**
     * Critic适配器不负责生成新正文，也不负责根据REVISE再次调用模型。
     * 它只把一次评审响应收敛为有限的结构化意见，下一次改写由Reflection Agent重新决定。
     */
    /** 当前Critic配置，后续换模型只需修改critic配置区。 */
    private final SampleFactoryModelConfiguration.Role role;
    /** 仅复用网络实现，不复用Actor的模型参数或对话记忆。 */
    private final OllamaSampleModelClient client;
    /** 严格解析模型评审意见，不修补JSON、不记录原始响应。 */
    private final ObjectMapper json;

    /** 只装配依赖，不因创建Bean触发评审。 */
    public OllamaSampleReflectionAdapter(SampleFactoryModelConfiguration models, OllamaSampleModelClient client, ObjectMapper json) {
        this.role = models.role(SampleReflectionAgent.Phase.REFLECT);
        this.client = client;
        this.json = json;
    }

    /** 独立评审角色的配置身份与输出上限，不把Actor上限用于Critic预留。 */
    @Override
    public SampleGenerationPort.Profile profile() {
        return role.profile();
    }

    /** 一次独立Critic请求；严格解析结果，不修补JSON，不在失败时调用第二次模型。 */
    @Override
    public SampleReflectionPort.Result reflect(SampleGenerationPort.Command command) {
        // 评审响应必须是单一JSON对象；任何多余文本或结构不符都按INVALID保守处理。
        var response = client.callOnce(role, command, SampleReflectionAgent.CRITIC_PROMPT);
        try {
            if (response.text().length() > 100000) return invalidReflection(response);
            JsonNode tree = json.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(response.text());
            if (tree == null || !tree.isObject() || !tree.path("outcome").isTextual() || !tree.path("issues").isArray())
                return invalidReflection(response);
            String outcome = tree.path("outcome").asText();
            var issuesNode = tree.path("issues");
            if (!Set.of("PASS", "REVISE", "REJECT").contains(outcome) || issuesNode.size() > 5)
                return invalidReflection(response);
            List<String> issues = new ArrayList<>();
            for (JsonNode item : issuesNode) {
                if (!item.isTextual() || item.asText().isBlank() || item.asText().length() > 500
                        || item.asText().codePoints().anyMatch(c -> Character.isISOControl(c) && c != '\n' && c != '\r' && c != '\t'))
                    return invalidReflection(response);
                issues.add(item.asText());
            }
            if ("PASS".equals(outcome) != issues.isEmpty()) return invalidReflection(response);
            return new SampleReflectionPort.Result(outcome, issues, response.inputTokens(), response.outputTokens(), response.measurement());
        } catch (java.io.IOException ex) {
            return invalidReflection(response);
        }
    }

    /** 非法评审也是已确认响应，保留消耗并保守拒绝，不能伪装成通过或零消耗。 */
    private SampleReflectionPort.Result invalidReflection(OllamaSampleModelClient.Response response) {
        // 即使结构非法，也要保留本次调用已发生的计量，不能返回零消耗或伪造PASS。
        return new SampleReflectionPort.Result("INVALID", List.of("模型评审未返回合法的结构化质量结论"),
                response.inputTokens(), response.outputTokens(), response.measurement());
    }

}
