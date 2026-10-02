package com.kant.agent.port.probe;

import com.kant.agent.contract.probe.ProbeCancellation;
import com.kant.agent.contract.probe.ProbeCommand;
import com.kant.agent.contract.probe.ProbeLookup;
import com.kant.agent.contract.probe.ProbeSubmission;
import com.kant.agent.contract.probe.TargetObservation;

import java.util.Optional;

/**
 * 共享探测执行端口。
 *
 * <p>实现必须先持久化执行意图，再调用外部目标；同一幂等键和相同参数
 * 返回原执行，不得创建第二个逻辑探测。</p>
 */
public interface ProbeExecutionPort {

    /**
     * 幂等受理并执行一次探测。
     *
     * @param command 受控探测命令
     * @return 提交结果
     */
    ProbeSubmission submit(ProbeCommand command);

    /**
     * 查询一次物理执行的当前观察。
     *
     * @param lookup 带身份边界的查询条件
     * @return 存在且允许访问时返回执行观察
     */
    Optional<TargetObservation> find(ProbeLookup lookup);

    /** 按身份和原幂等键查询已受理操作，恢复时不重新发出物理调用。 */
    Optional<TargetObservation> findByIdempotencyKey(String tenantId, String actorId, String key);

    /**
     * 记录取消意图。
     *
     * <p>并非所有目标客户端都能中断已发出的物理请求，因此该操作不能
     * 承诺远端目标一定停止。</p>
     *
     * @param lookup 带身份边界的执行查询条件
     * @return 取消意图受理结果
     */
    ProbeCancellation requestCancellation(ProbeLookup lookup);
}
