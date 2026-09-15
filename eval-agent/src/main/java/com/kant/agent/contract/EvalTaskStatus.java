package com.kant.agent.contract;

/**
 * 红队评测任务状态。
 *
 * <p>任务状态由编排器在一次评测执行过程中按业务阶段推进，前端可以根据该状态
 * 展示任务进度，仓储也可以据此判断任务是否已经结束。</p>
 */
public enum EvalTaskStatus {
    /** 任务记录已经创建，但尚未开始参数校验。 */
    CREATED,
    /** 正在校验目标模型、风险类别和种子等请求参数。 */
    VALIDATING,
    /** 正在生成攻击样本并调用目标模型。 */
    RUNNING,
    /** 目标模型调用成功，正在由 Judge 分析响应。 */
    JUDGING,
    /** 评测和判定均已完成。 */
    COMPLETED,
    /** 评测过程中发生异常或目标模型调用失败。 */
    FAILED
}
