package com.kant.llm.eval.agent.production.config;

import com.kant.agent.port.production.SampleGenerationPort;
import com.kant.agent.service.production.SampleGenerationWorkflow;
import com.kant.agent.service.production.SampleReflectionAgent;
import com.kant.llm.eval.agent.production.dto.FactoryResponses;
import com.kant.llm.eval.agent.production.support.FactoryJsonSupport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.TreeMap;

/**
 * D05两个模型角色的独立配置及组合快照，不发起网络调用。
 * <p>generator负责生成，critic负责质量评审；critic未配置的字段在启动时沿用generator，
 * 便于先使用同一个模型，后续只覆盖critic字段即可换模型。显式false或空模型名不会自动回退。</p>
 * <p>计划绑定组合指纹，单次命令绑定当前角色指纹；所有摘要均使用排序Map，避免重启改变指纹。
 * 配置在Bean创建时冻结，不提供运行中的隐式热切换；调整配置后需重启并重置/新建计划。</p>
 */
@Component
public class SampleFactoryModelConfiguration {
    /**
     * 该配置对象在应用启动时冻结两套角色配置。
     * 计划保存的是组合指纹，具体操作保存的是当前阶段角色指纹；两层校验共同防止运行中的隐式换模。
     */
    /** 独立Actor配置，不与Critic共享可变对象。 */
    private final Role generator;
    /** 独立Critic配置，物理模型允许暂时与Actor相同。 */
    private final Role critic;
    /** 同时约束两个角色和Agent质量版本的计划快照；不包含私有提示词或服务地址。 */
    private final FactoryResponses.ModelProfile profile;

    /** 读取两个命名空间；只做本地参数校验和稳定摘要，不探活模型、不访问数据库。 */
    public SampleFactoryModelConfiguration(
            @Value("${eval.agent.sample-factory.generator.enabled:false}") boolean generatorEnabled,
            @Value("${eval.agent.sample-factory.generator.base-url:http://localhost:11434}") String generatorBaseUrl,
            @Value("${eval.agent.sample-factory.generator.model:}") String generatorModel,
            @Value("${eval.agent.sample-factory.generator.max-output-tokens:1024}") int generatorOutputLimit,
            @Value("${eval.agent.sample-factory.generator.temperature:0.7}") double generatorTemperature,
            @Value("${eval.agent.sample-factory.generator.request-timeout-seconds:120}") int generatorTimeout,
            @Value("${eval.agent.sample-factory.critic.enabled:${eval.agent.sample-factory.generator.enabled:false}}") boolean criticEnabled,
            @Value("${eval.agent.sample-factory.critic.base-url:${eval.agent.sample-factory.generator.base-url:http://localhost:11434}}") String criticBaseUrl,
            @Value("${eval.agent.sample-factory.critic.model:${eval.agent.sample-factory.generator.model:}}") String criticModel,
            @Value("${eval.agent.sample-factory.critic.max-output-tokens:${eval.agent.sample-factory.generator.max-output-tokens:1024}}") int criticOutputLimit,
            @Value("${eval.agent.sample-factory.critic.temperature:${eval.agent.sample-factory.generator.temperature:0.7}}") double criticTemperature,
            @Value("${eval.agent.sample-factory.critic.request-timeout-seconds:${eval.agent.sample-factory.generator.request-timeout-seconds:120}}") int criticTimeout,
            FactoryJsonSupport json) {
        generator = role("GENERATOR", generatorEnabled, generatorBaseUrl, generatorModel, generatorOutputLimit,
                generatorTemperature, generatorTimeout, SampleGenerationWorkflow.VERSION, json);
        critic = role("CRITIC", criticEnabled, criticBaseUrl, criticModel, criticOutputLimit,
                criticTemperature, criticTimeout, SampleReflectionAgent.CRITIC_VERSION, json);
        Map<String, Object> fingerprint = new TreeMap<>();
        fingerprint.put("profileVersion", "sample-role-pair-v1");
        fingerprint.put("agentVersion", SampleReflectionAgent.VERSION);
        fingerprint.put("qualityVersion", SampleReflectionAgent.QUALITY_VERSION);
        fingerprint.put("generatorProfileRef", generator.profile().profileRef());
        fingerprint.put("criticProfileRef", critic.profile().profileRef());
        profile = new FactoryResponses.ModelProfile("genprofile_" + json.hash(fingerprint),
                generator.profile().available() && critic.profile().available(),
                generator.profile().modelName(), generator.profile().maxOutputTokens(), generator.profile(), critic.profile());
    }

    /** 对外保留旧Profile顶层字段，并附加两个角色的独立快照；不触发模型调用。 */
    public FactoryResponses.ModelProfile profile() {
        // 只返回可对外展示的快照；Ollama地址、温度和超时留在后端角色配置中。
        return profile;
    }

    /** 根据执行阶段选择角色，评审不得误用生成角色的输出上限、地址或超时。 */
    public Role role(SampleReflectionAgent.Phase phase) {
        // GENERATE使用Actor，REFLECT使用Critic；即使两个角色物理模型相同，也不能混用角色身份。
        return switch (phase) {
            case GENERATE -> generator;
            case REFLECT -> critic;
        };
    }

    /** 只有两个角色都启用且组合指纹完全相同，才允许为冻结计划新增模型调用。 */
    public boolean matches(String profileRef) {
        // 计划快照不匹配时停止新增调用，让调用方通过重置计划显式接受新配置。
        return profile.available() && profile.profileRef().equals(profileRef);
    }

    /** 构建角色快照；角色名进入摘要，即使同模型同参数，生成与评审身份也不混用。 */
    private Role role(String name, boolean enabled, String baseUrl, String model, int outputLimit,
                      double temperature, int timeout, String promptVersion, FactoryJsonSupport json) {
        // 空模型名可以在配置层保留为不可用状态，但地址、数值范围等结构性错误直接拒绝启动。
        if (baseUrl == null || baseUrl.isBlank() || model == null)
            throw new IllegalArgumentException(name + "模型地址或名称配置不合法");
        if (outputLimit < 64 || outputLimit > 4096 || !Double.isFinite(temperature) || temperature < 0 || temperature > 2)
            throw new IllegalArgumentException(name + "输出限制或温度配置不合法");
        if (timeout < 10 || timeout > 600)
            throw new IllegalArgumentException(name + "请求超时为10至600秒");
        Map<String, Object> fingerprint = new TreeMap<>();
        fingerprint.put("adapterVersion", "ollama-sample-role-v1");
        fingerprint.put("role", name);
        fingerprint.put("baseUrl", baseUrl);
        fingerprint.put("model", model);
        fingerprint.put("outputLimit", outputLimit);
        fingerprint.put("temperature", temperature);
        fingerprint.put("requestTimeoutSeconds", timeout);
        fingerprint.put("promptVersion", promptVersion);
        // enabled控制可用性但不改变身份，关闭再开启相同配置不需要制造另一个模型身份。
        var snapshot = new SampleGenerationPort.Profile("modelrole_" + json.hash(fingerprint), enabled && !model.isBlank(), model, outputLimit);
        return new Role(snapshot, baseUrl, temperature, timeout);
    }

    /**
     * 不可变的单角色部署配置，仅在后端使用，不把地址返回前端。
     * @param profile 角色指纹、可用性、模型名和输出Token上限
     * @param baseUrl 本角色独立Ollama地址，不是被测目标
     * @param temperature 本角色采样温度
     * @param requestTimeoutSeconds 本角色读取超时，不等于供应商执行期限
     */
    public record Role(SampleGenerationPort.Profile profile, String baseUrl, double temperature,
                       int requestTimeoutSeconds) { }
}
