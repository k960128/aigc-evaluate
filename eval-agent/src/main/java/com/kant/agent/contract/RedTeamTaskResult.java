package com.kant.agent.contract;

import java.util.List;

/**
 * 单轮红队评测结果。
 *
 * <p>该对象是 M1 同步用例的统一输出：无论任务成功还是失败，都尽量保留已经产生的
 * 样本、目标模型响应、Judge 结论和事件，便于调用方定位失败阶段。</p>
 *
 * @param taskId 评测任务唯一标识
 * @param status 任务最终状态
 * @param sample 已创建的攻击样本；在参数校验失败时为空
 * @param targetResponse 目标模型调用结果；尚未调用时为空
 * @param verdict Judge 判定结果；目标模型调用失败或尚未判定时为空
 * @param events 任务执行期间产生的事件，按发生顺序排列
 * @param errorMessage 任务失败原因；成功时为空
 */
public record RedTeamTaskResult(
        String taskId,
        EvalTaskStatus status,
        AttackSample sample,
        TargetModelResponse targetResponse,
        JudgeVerdict verdict,
        List<AgentEvent> events,
        String errorMessage
) {
    /**
     * 对事件列表做不可变拷贝，保证任务结果返回后不会被外部修改。
     *
     * @param events 任务事件列表
     */
    public RedTeamTaskResult {
        events = events == null ? List.of() : List.copyOf(events);
    }
}
