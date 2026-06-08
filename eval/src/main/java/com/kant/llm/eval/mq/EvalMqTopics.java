package com.kant.llm.eval.mq;

/**
 * 评测任务 MQ Topic 常量。
 */
public final class EvalMqTopics {

    private EvalMqTopics() {
    }

    /** 任务批次拆分 Topic：消费端负责把一次任务运行拆成多条样本结果。 */
    public static final String EVAL_TASK_SPLIT = "EvalTaskSplit_MQ";

    /** 单样本执行 Topic：后续接入被测模型调用与 L1/L2/L3 漏斗判定。 */
    public static final String EXECUTION = "Execution_MQ";
}
