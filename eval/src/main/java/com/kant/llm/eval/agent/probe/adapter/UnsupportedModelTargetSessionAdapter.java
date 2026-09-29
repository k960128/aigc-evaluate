package com.kant.llm.eval.agent.probe.adapter;

import com.kant.agent.contract.common.ExecutionContext;
import com.kant.agent.contract.target.TargetSessionReference;
import com.kant.agent.contract.target.TargetSnapshot;
import com.kant.agent.port.target.TargetSessionPort;
import com.kant.framework.exception.ClientException;
import com.kant.llm.eval.agent.probe.error.AgentProbeErrorCode;
import org.springframework.stereotype.Component;

/**
 * D01 模型目标的会话端口实现。
 *
 * <p>该实现故意明确拒绝所有会话操作，用于防止调用方把多次独立模型调用
 * 当成真实多轮会话。D03 引入具备会话语义的目标适配器后再替换或路由。</p>
 */
@Component
public class UnsupportedModelTargetSessionAdapter implements TargetSessionPort {

    @Override
    public TargetSessionReference open(ExecutionContext context, TargetSnapshot targetSnapshot) {
        throw unsupported();
    }

    @Override
    public TargetSessionReference reset(ExecutionContext context, TargetSessionReference session) {
        throw unsupported();
    }

    @Override
    public TargetSessionReference branch(ExecutionContext context, TargetSessionReference session) {
        throw unsupported();
    }

    @Override
    public void close(ExecutionContext context, TargetSessionReference session) {
        throw unsupported();
    }

    private ClientException unsupported() {
        return new ClientException("D01模型目标不支持多轮会话、重置、关闭或分支操作",
                AgentProbeErrorCode.TARGET_CAPABILITY_UNSUPPORTED);
    }
}
