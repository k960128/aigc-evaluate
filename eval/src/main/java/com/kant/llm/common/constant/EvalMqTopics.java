package com.kant.llm.common.constant;

/**
 * 评测任务 MQ Topic 常量。
 */
public final class EvalMqTopics {

    private EvalMqTopics() {
    }

    /**
     * L1 风险词库 AC 自动机版本发布通知。
     *
     */
    public static final String RISK_VOCABULARY_AC_PUBLISH_TOPIC = "RiskVocabularyAcPublish_MQ";

    /**
     * 任务批次拆分 Topic：消费端负责把一次任务运行拆成多条样本结果。
     *
     */
    public static final String EVAL_TASK_SPLIT_TOPIC = "EvalTaskSplit_MQ";

    /**
     * 单样本执行 Topic：后续接入被测模型调用与 L1/L2/L3 漏斗判定。
     *
     */
    public static final String EXECUTION_TOPIC = "Execution_MQ";

    /**
     * 单样本模型调用 Topic：只负责调用被评测模型，并把模型输出落库。
     */
    public static final String MODEL_CALL_TOPIC = "ModelCall_MQ";

    /**
     * 单样本安全评测 Topic：模型调用完成后执行 L1/L2/L3 流水线。
     */
    public static final String SAFETY_EVALUATE_TOPIC = "SafetyEvaluate_MQ";

    /**
     * L2 知识库一键同步批次 Topic：消费端负责扫描待同步知识并拆成单条同步事件。
     *
     */
    public static final String L2_KB_SYNC_BATCH = "L2KbSyncBatch_MQ";

    /**
     * L2 知识库单条同步事件 Topic：消费端负责写入 ES 和 PG 向量库。
     *
     */
    public static final String L2_KB_SYNC_EVENT = "L2KbSyncEvent_MQ";

    /**
     * 评测结果与流水线节点 ES 投影同步事件 Topic。
     *
     * <p>消费者回查 MySQL 最新数据后写入 Easy-ES 管理的结果/节点索引。</p>
     */
    public static final String EVAL_ES_SYNC_EVENT_TOPIC = "EvalEsSyncEvent_MQ";

    /**
     * 知识特征版本发布 Topic。
     */
    public static final String KNOWLEDGE_PUBLISH_VERSION_TOPIC = "KNOWLEDGE_PUBLISH_VERSION_TOPIC";

}
