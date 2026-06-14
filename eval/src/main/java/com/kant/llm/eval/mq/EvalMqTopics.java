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

    /** L2 知识库一键同步批次 Topic：消费端负责扫描待同步知识并拆成单条同步事件。 */
    public static final String L2_KB_SYNC_BATCH = "L2KbSyncBatch_MQ";

    /** L2 知识库单条同步事件 Topic：消费端负责写入 ES 和 PG 向量库。 */
    public static final String L2_KB_SYNC_EVENT = "L2KbSyncEvent_MQ";
}
