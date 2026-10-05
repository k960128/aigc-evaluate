package com.kant.agent.port.production;

import com.kant.agent.contract.common.ContractChecks;

import java.util.List;
import java.util.Set;

/**
 * 样本反思评审端口：只评价候选，不生成资产、不执行目标、不调用业务工具。
 * 一次reflect只允许一次物理模型请求；生成与评审分别由外层持久化、预留和结算。
 */
public interface SampleReflectionPort {
    /** 只读Critic独立配置身份、可用性和输出上限，不调用模型，不返回Actor配置。 */
    SampleGenerationPort.Profile profile();

    /** 按Critic自己的配置快照执行一次评审，不在端口内部循环改写。 */
    Result reflect(SampleGenerationPort.Command command);

    /**
     * 已确认的模型评审结果。只保留短反馈，不保存模型内部思维链或非法原始响应。
     * @param outcome PASS通过、REVISE建议修改、REJECT不应继续、INVALID响应结构非法
     * @param issues 最多5条、每条最多500字符的修改意见；PASS必须为空，其他结果必须有原因
     * @param inputTokens 本次评审输入用量，非负
     * @param outputTokens 本次评审输出用量，非负；非法响应也计量
     * @param measurement ACTUAL供应商实报，ESTIMATED平台估算
     */
    record Result(String outcome, List<String> issues, long inputTokens, long outputTokens, String measurement) {
        public Result {
            ContractChecks.require(outcome != null && Set.of("PASS", "REVISE", "REJECT", "INVALID").contains(outcome), "反思结果类型不合法");
            issues = List.copyOf(issues);
            ContractChecks.require(issues.size() <= 5, "反思意见过多");
            issues.forEach(issue -> ContractChecks.maxLength(ContractChecks.notBlankPreserving(issue, "issue"), 500, "issue"));
            ContractChecks.require("PASS".equals(outcome) == issues.isEmpty(), "反思结果与意见不一致");
            ContractChecks.nonNegative(inputTokens, "inputTokens");
            ContractChecks.nonNegative(outputTokens, "outputTokens");
            ContractChecks.require("ACTUAL".equals(measurement) || "ESTIMATED".equals(measurement), "反思计量类型不合法");
        }
    }
}
