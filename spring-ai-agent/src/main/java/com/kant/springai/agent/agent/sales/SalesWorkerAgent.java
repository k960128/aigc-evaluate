package com.kant.springai.agent.agent.sales;

/**
 * 由销售 Supervisor 调度的专业 Worker Agent。
 */
public interface SalesWorkerAgent {

    /**
     * Supervisor 路由时使用的稳定标识。
     */
    String name();

    /**
     * 提供给 Supervisor 的能力描述。
     */
    String description();

    /**
     * 执行 Supervisor 分配的任务。
     */
    String execute(WorkerRequest request);

    /**
     * Worker 的单次执行输入。
     *
     * @param question 用户的原始问题
     * @param instruction Supervisor 分配给当前 Worker 的具体指令
     * @param today 当前日期
     * @param history 已完成的 Agent 执行记录
     */
    record WorkerRequest(
            String question,
            String instruction,
            String today,
            String history) {
    }
}
