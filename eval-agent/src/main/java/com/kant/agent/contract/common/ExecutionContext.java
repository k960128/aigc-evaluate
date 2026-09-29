package com.kant.agent.contract.common;

/**
 * 一次智能体执行的统一业务上下文。
 *
 * <p>这些标识用于把活动、任务、运行、尝试、操作者和授权记录串联起来。
 * {@code traceId} 只负责技术链路追踪，不能替代任何业务主键。</p>
 *
 * @param tenantId        业务空间或租户标识，由服务端认证上下文确定
 * @param actorId         当前操作者标识，由服务端认证上下文确定
 * @param activityId      评测活动标识
 * @param taskId          评测任务标识
 * @param runId           本次运行标识
 * @param attemptId       本次攻击尝试标识
 * @param traceId         技术链路追踪标识
 * @param authorizationRef 本次生效的运行授权引用
 */
public record ExecutionContext(
        String tenantId,
        String actorId,
        String activityId,
        String taskId,
        String runId,
        String attemptId,
        String traceId,
        String authorizationRef) {

    /**
     * 创建上下文时立即检查所有执行必填标识。
     */
    public ExecutionContext {
        tenantId = ContractChecks.maxLength(
                ContractChecks.notBlank(tenantId, "tenantId"), 64, "tenantId");
        actorId = ContractChecks.maxLength(
                ContractChecks.notBlank(actorId, "actorId"), 64, "actorId");
        activityId = ContractChecks.maxLength(
                ContractChecks.notBlank(activityId, "activityId"), 64, "activityId");
        taskId = ContractChecks.maxLength(
                ContractChecks.notBlank(taskId, "taskId"), 64, "taskId");
        runId = ContractChecks.maxLength(
                ContractChecks.notBlank(runId, "runId"), 64, "runId");
        attemptId = ContractChecks.maxLength(
                ContractChecks.notBlank(attemptId, "attemptId"), 64, "attemptId");
        traceId = ContractChecks.maxLength(
                ContractChecks.notBlank(traceId, "traceId"), 64, "traceId");
        authorizationRef = ContractChecks.maxLength(
                ContractChecks.notBlank(authorizationRef, "authorizationRef"), 128, "authorizationRef");
    }
}
