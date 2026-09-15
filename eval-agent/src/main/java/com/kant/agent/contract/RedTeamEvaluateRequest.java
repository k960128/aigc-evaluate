package com.kant.agent.contract;

/**
 * 创建一次单轮红队评测所需的业务输入。
 *
 * <p>第一阶段使用固定样本和 Mock 目标模型验证业务闭环。模型连接信息使用字符串表达，
 * 避免 contract 包依赖 infra-ai 的枚举和 Spring AI 类型。该对象只描述一次用例的输入，
 * 不承担样本生成、模型调用或结果判定职责。</p>
 *
 * @param targetModel 目标模型名称或模型标识
 * @param targetManufacturer 目标模型厂商，未填写时使用 MOCK
 * @param targetBaseUrl 目标模型服务地址；使用 Mock 调用时可以为空
 * @param targetApiKey 目标模型访问凭证；使用 Mock 调用时可以为空
 * @param targetModelId 目标模型在系统中的标识，未填写时使用 1
 * @param riskCategory 本次评测所属的风险类别
 * @param seedContent 攻击样本种子内容
 * @param ruleVersion Judge 使用的规则或提示词版本
 * @param mockTargetResponse 测试阶段使用的固定目标模型响应，生产调用时可以为空
 */
public record RedTeamEvaluateRequest(
        String targetModel,
        String targetManufacturer,
        String targetBaseUrl,
        String targetApiKey,
        Long targetModelId,
        String riskCategory,
        String seedContent,
        String ruleVersion,
        String mockTargetResponse
) {
    /**
     * 返回规范化后的目标模型厂商。
     *
     * <p>统一转为大写是为了与基础设施层的厂商枚举匹配；空值时使用 MOCK，
     * 这样 M1 可以在没有外部模型服务的情况下完整跑通。</p>
     *
     * @return 规范化后的厂商名称
     */
    public String actualManufacturer() {
        return isBlank(targetManufacturer) ? "MOCK" : targetManufacturer.trim().toUpperCase();
    }

    /**
     * 返回目标模型的实际数值标识。
     *
     * @return 请求中的模型 ID，未提供时返回默认值 1
     */
    public long actualTargetModelId() {
        return targetModelId == null ? 1L : targetModelId;
    }

    /**
     * 返回 Judge 使用的实际规则版本。
     *
     * @return 请求中的规则版本，未提供时返回 rule-v1
     */
    public String actualRuleVersion() {
        return isBlank(ruleVersion) ? "rule-v1" : ruleVersion;
    }

    /**
     * 校验创建评测任务所需的最小参数。
     *
     * <p>这里只做用例级别的必填校验。厂商、地址和凭证等更具体的连接校验由目标
     * 模型网关负责，这样领域契约不会绑定某个厂商的认证规则。</p>
     *
     * @throws IllegalArgumentException 当必填参数为空时抛出
     */
    public void validate() {
        if (isBlank(targetModel)) {
            throw new IllegalArgumentException("targetModel 不能为空");
        }
        if (isBlank(riskCategory)) {
            throw new IllegalArgumentException("riskCategory 不能为空");
        }
        if (isBlank(seedContent)) {
            throw new IllegalArgumentException("seedContent 不能为空");
        }
    }

    /**
     * 判断字符串是否为空或只包含空白字符。
     *
     * @param value 待判断的字符串
     * @return 字符串为 null、空串或全为空白时返回 true
     */
    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
