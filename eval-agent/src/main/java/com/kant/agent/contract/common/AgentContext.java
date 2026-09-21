package com.kant.agent.contract.common;

import java.time.Instant;

/**
 * 单次智能体调用的执行上下文。
 *
 * <p>本对象由后端创建，用于业务关联、链路追踪和执行时间控制。
 * 不应直接接收模型生成的标识，也不应允许前端自行指定操作人。</p>
 *
 * <p>这里只保存调用级上下文。
 * 评测目标、数据集、风险规则等业务内容通过各智能体的输入对象传入。</p>
 *
 * <p>本对象不是聊天记忆，也不是任务检查点。</p>
 *
 * @param activityId  评测活动 ID，用于关联同一活动下的计划、审批和执行记录
 * @param agentRunId  单次智能体执行 ID，每次独立执行时由后端生成
 * @param traceId     链路追踪 ID，用于关联本次处理过程中的日志和调用
 * @param operatorId  操作人 ID，由后端从可信身份上下文取得；
 *                    后台继续执行时保留活动对应的操作人引用
 * @param deadlineAt  本次智能体执行的截止时间，使用 UTC 时间点表示
 */
public record AgentContext(
        String activityId,
        String agentRunId,
        String traceId,
        Long operatorId,
        Instant deadlineAt
) {

    /**
     * 校验上下文中的基础标识和必填字段。
     *
     * <p>这里只检查数据合法性，不执行权限查询。
     * operatorId 存在不代表该用户已经获得执行权限。</p>
     */
    public AgentContext {
        activityId = requireText(activityId, "activityId");
        agentRunId = requireText(agentRunId, "agentRunId");
        traceId = requireText(traceId, "traceId");

        if (operatorId == null || operatorId <= 0) {
            throw new IllegalArgumentException("operatorId 必须为正整数");
        }

        if (deadlineAt == null) {
            throw new IllegalArgumentException("deadlineAt 不能为空");
        }
    }

    /**
     * 判断在指定时间点是否已经到达执行截止时间。
     *
     * <p>当前时间由调用方传入，避免在对象内部隐式读取系统时间。
     * 后续编排器应在发起模型调用或工具调用之前检查。</p>
     *
     * @param currentTime 当前时间
     * @return 当前时间等于或晚于截止时间时返回 true
     */
    public boolean isExpired(Instant currentTime) {
        if (currentTime == null) {
            throw new IllegalArgumentException("currentTime 不能为空");
        }

        return !currentTime.isBefore(deadlineAt);
    }

    /**
     * 校验字符串非空，并移除首尾空白。
     *
     * @param value     待校验内容
     * @param fieldName 字段名称
     * @return 规范化后的字符串
     */
    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " 不能为空");
        }

        return value.strip();
    }
}