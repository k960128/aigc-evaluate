package com.kant.agent.port;

import com.kant.agent.contract.common.AgentContext;
import com.kant.agent.contract.evaluation.EvaluationExecutionSnapshot;
import com.kant.agent.contract.evaluation.EvaluationSubmissionCommand;
import com.kant.agent.contract.evaluation.EvaluationTaskReference;

/**
 * 对接现有评测执行引擎的业务接口。
 *
 * <p>该接口不重新实现模型调用和 L1/L2/L3 判定。</p>
 */
public interface EvaluationPipelinePort {

    /**
     * 创建并提交现有评测任务，返回准确的任务和批次引用。
     *
     * <p>实现要求：
     * 同一幂等键对应同一次提交；
     * 参数不一致时不能复用该幂等键；
     * 提交结果未知时先核对，不能盲目重新创建；
     * 执行范围和预算必须被实际执行链路落实。</p>
     *
     * <p>如果现有流水线暂不支持某项约束，
     * 适配层必须明确拒绝不支持的请求，不能静默忽略。</p>
     */
    EvaluationTaskReference submit(
            EvaluationSubmissionCommand command,
            AgentContext context
    );

    /**
     * 查询指定批次，而不是查询某任务的最近一次批次。
     */
    EvaluationExecutionSnapshot query(
            EvaluationTaskReference reference,
            AgentContext context
    );
}