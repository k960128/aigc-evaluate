package com.kant.llm.eval.agent.probe.adapter;

import com.kant.agent.contract.target.TargetCapabilities;
import com.kant.agent.contract.target.TargetCapability;
import com.kant.agent.contract.target.TargetReference;
import com.kant.agent.contract.target.TargetSnapshot;
import com.kant.agent.contract.target.TargetStatus;
import com.kant.agent.contract.target.TargetType;
import com.kant.llm.eval.basesource.dao.entity.ModelInfoDO;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 将现有 {@code model_info} 配置转换为不含凭据的目标快照。
 */
@Component
public class ModelTargetSnapshotFactory {

    /** 已完成消息列表适配的模型客户端厂商。配置声明不能替代客户端能力。 */
    private static final Set<String> MULTI_TURN_MANUFACTURERS = Set.of(
            "DEEPSEEK", "GPT", "OPENAI", "OLLAMA", "QWEN", "GLM");

    /**
     * 根据模型配置创建当前快照。
     *
     * @param modelInfo 现有模型配置
     * @return 不含 API Key 和基础地址的目标快照
     */
    public TargetSnapshot create(ModelInfoDO modelInfo) {
        // 能力声明发生变化也必须改变快照；不在引用中暴露配置或凭据。
        String version = versionToken(modelInfo)
                + (supportsMultiTurn(modelInfo.getConfig(), modelInfo.getManufacturerCode()) ? "-messages-v1" : "");
        String targetId = String.valueOf(modelInfo.getId());
        String snapshotRef = "model:" + targetId + ":" + version;
        Map<String, Object> config = modelInfo.getConfig();
        String environment = truncate(configText(config, "environment", "UNSPECIFIED"), 64);
        // 只生成平台内部配置引用，不读取扩展配置中的自由文本，避免误把凭据写入快照。
        String identityRef = "model-credential:" + targetId;

        List<String> limitations = new ArrayList<>();
        limitations.add("D01模型适配器仅支持无状态单轮文本调用");
        limitations.add("当前模型客户端不提供物理请求状态查询");
        limitations.add("当前模型响应不提供供应商真实Token与费用数据，平台仅能提供Token估算");
        if ("UNSPECIFIED".equals(environment)) {
            limitations.add("模型配置未声明测试环境");
        }

        EnumSet<TargetCapability> capabilities = EnumSet.of(TargetCapability.SINGLE_TURN);
        if (supportsMultiTurn(config, modelInfo.getManufacturerCode())) {
            // D03 使用平台持久化的用户/助手消息历史重新构造同一线性会话。
            // 只有模型配置明确声明后，才对外暴露多轮能力。
            capabilities.add(TargetCapability.MULTI_TURN);
            limitations.remove("D01模型适配器仅支持无状态单轮文本调用");
            limitations.add("D03当前仅支持线性多轮，不支持目标会话重置或分支");
        }

        String displayName = truncate(
                firstNonBlank(modelInfo.getModel(), modelInfo.getModelDescribe(), "model-" + targetId),
                256);
        String modelName = truncate(firstNonBlank(modelInfo.getModel()), 256);
        return new TargetSnapshot(
                snapshotRef,
                new TargetReference(TargetType.MODEL, targetId),
                displayName,
                modelName,
                version,
                Boolean.TRUE.equals(modelInfo.getStatus()) ? TargetStatus.AVAILABLE : TargetStatus.DISABLED,
                environment,
                identityRef,
                new TargetCapabilities(capabilities),
                Instant.now(),
                List.copyOf(limitations));
    }

    /**
     * 读取模型配置中明确的多轮能力声明。
     *
     * <p>默认关闭，避免把普通单轮客户端误标为支持多轮。</p>
     */
    private boolean supportsMultiTurn(Map<String, Object> config, String manufacturerCode) {
        if (config == null || config.get("supportsMultiTurn") == null) {
            return false;
        }
        if (manufacturerCode == null
                || !MULTI_TURN_MANUFACTURERS.contains(
                manufacturerCode.trim().toUpperCase(Locale.ROOT))) {
            return false;
        }
        Object value = config.get("supportsMultiTurn");
        return value instanceof Boolean
                ? (Boolean) value
                : Boolean.parseBoolean(String.valueOf(value));
    }

    /**
     * 生成可比较的模型配置版本标识。
     */
    private String versionToken(ModelInfoDO modelInfo) {
        if (modelInfo.getVersion() != null) {
            return "v" + modelInfo.getVersion();
        }
        if (modelInfo.getUpdateTime() != null) {
            return "updated-" + modelInfo.getUpdateTime().toInstant(ZoneOffset.UTC).toEpochMilli();
        }
        return "unversioned";
    }

    private String configText(Map<String, Object> config, String key, String defaultValue) {
        if (config == null || config.get(key) == null) {
            return defaultValue;
        }
        String value = String.valueOf(config.get(key)).trim();
        return value.isEmpty() ? defaultValue : value;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
