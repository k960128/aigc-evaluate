package com.kant.agent.port.target;

import com.kant.agent.contract.common.ExecutionContext;
import com.kant.agent.contract.target.TargetSessionReference;
import com.kant.agent.contract.target.TargetSnapshot;

import java.util.List;

/**
 * 管理目标侧会话生命周期的端口。
 *
 * <p>D03 的 MODEL 适配器支持已确认消息重放；创建、关闭由运行编排器原子
 * 管理。未支持的独立生命周期、重置和分支必须明确拒绝。</p>
 */
public interface TargetSessionPort {

    /** 创建一个目标会话。 */
    TargetSessionReference open(ExecutionContext context, TargetSnapshot targetSnapshot);

    /** 重置已有目标会话。 */
    TargetSessionReference reset(ExecutionContext context, TargetSessionReference session);

    /** 从已有会话创建受控分支。 */
    TargetSessionReference branch(ExecutionContext context, TargetSessionReference session);

    /** 关闭目标会话。 */
    void close(ExecutionContext context, TargetSessionReference session);

    /**
     * 按当前身份读取已确认的会话历史。
     *
     * <p>返回的历史必须来自已经完成并保存的目标执行。尚未确认的调用不能
     * 被拼接进下一轮请求。</p>
     */
    List<com.kant.agent.contract.target.TargetSessionMessage> history(
            ExecutionContext context,
            TargetSessionReference session);

    /** 按身份和会话标识读取平台会话引用。 */
    TargetSessionReference find(ExecutionContext context, String sessionRef);
}
