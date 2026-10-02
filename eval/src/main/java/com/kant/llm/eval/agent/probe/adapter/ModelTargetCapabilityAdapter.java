package com.kant.llm.eval.agent.probe.adapter;

import com.kant.agent.contract.target.TargetReference;
import com.kant.agent.contract.target.TargetSnapshot;
import com.kant.agent.contract.target.TargetType;
import com.kant.agent.port.target.TargetCapabilityPort;
import com.kant.framework.exception.ClientException;
import com.kant.llm.eval.agent.probe.error.AgentProbeErrorCode;
import com.kant.llm.eval.basesource.dao.entity.ModelInfoDO;
import com.kant.llm.eval.basesource.service.ModelInfoService;
import org.springframework.stereotype.Component;

/**
 * 基于现有模型配置表的目标能力适配器。
 *
 * <p>D01 只声明模型支持无状态单轮文本调用。即使某个模型服务理论上支持
 * 多轮，也不能在尚未实现会话语义前对外声明。</p>
 */
@Component
public class ModelTargetCapabilityAdapter implements TargetCapabilityPort {

    private final ModelInfoService modelInfoService;
    private final ModelTargetSnapshotFactory snapshotFactory;

    public ModelTargetCapabilityAdapter(ModelInfoService modelInfoService,
                                        ModelTargetSnapshotFactory snapshotFactory) {
        this.modelInfoService = modelInfoService;
        this.snapshotFactory = snapshotFactory;
    }

    @Override
    public TargetSnapshot getSnapshot(String tenantId, String actorId, TargetReference target) {
        if (target.targetType() != TargetType.MODEL) {
            throw new ClientException("D01仅支持MODEL类型目标",
                    AgentProbeErrorCode.TARGET_CAPABILITY_UNSUPPORTED);
        }
        Long modelId;
        try {
            modelId = Long.valueOf(target.targetId());
        } catch (NumberFormatException ex) {
            throw new ClientException("模型目标ID必须是有效数字",
                    AgentProbeErrorCode.INVALID_PROBE_REQUEST);
        }
        ModelInfoDO modelInfo = modelInfoService.getById(modelId);
        if (modelInfo == null || Boolean.TRUE.equals(modelInfo.getDeleted())) {
            throw new ClientException("模型配置不存在，modelId=" + modelId,
                    AgentProbeErrorCode.TARGET_UNAVAILABLE);
        }
        return snapshotFactory.create(modelInfo);
    }
}
