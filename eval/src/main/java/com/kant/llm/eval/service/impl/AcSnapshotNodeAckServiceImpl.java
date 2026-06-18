package com.kant.llm.eval.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kant.llm.eval.common.enums.AcSnapshotAckStatusEnums;
import com.kant.llm.eval.dao.entity.AcSnapshotNodeAckDO;
import com.kant.llm.eval.dao.mapper.AcSnapshotNodeAckMapper;
import com.kant.llm.eval.service.AcSnapshotNodeAckService;
import org.springframework.stereotype.Service;

@Service
public class AcSnapshotNodeAckServiceImpl
        extends ServiceImpl<AcSnapshotNodeAckMapper, AcSnapshotNodeAckDO>
        implements AcSnapshotNodeAckService {

    @Override
    public void ackSuccess(Long versionId, String nodeId, String stage, String hash) {
        upsert(versionId, nodeId, stage, AcSnapshotAckStatusEnums.SUCCESS.getCode(), hash, null);
    }

    @Override
    public void ackFailed(Long versionId, String nodeId, String stage, String hash, String errorMsg) {
        upsert(versionId, nodeId, stage, AcSnapshotAckStatusEnums.FAILED.getCode(), hash, trimError(errorMsg));
    }

    private void upsert(Long versionId, String nodeId, String stage, String status, String hash, String errorMsg) {
        AcSnapshotNodeAckDO existing = getOne(new LambdaQueryWrapper<AcSnapshotNodeAckDO>()
                .eq(AcSnapshotNodeAckDO::getVersionId, versionId)
                .eq(AcSnapshotNodeAckDO::getNodeId, nodeId)
                .eq(AcSnapshotNodeAckDO::getStage, stage)
                .last("LIMIT 1"));
        if (existing == null) {
            existing = new AcSnapshotNodeAckDO();
            existing.setVersionId(versionId);
            existing.setNodeId(nodeId);
            existing.setStage(stage);
            existing.setStatus(status);
            existing.setHash(hash);
            existing.setErrorMsg(errorMsg);
            save(existing);
            return;
        }
        existing.setStatus(status);
        existing.setHash(hash);
        existing.setErrorMsg(errorMsg);
        updateById(existing);
    }

    private static String trimError(String errorMsg) {
        if (errorMsg == null || errorMsg.length() <= 1000) {
            return errorMsg;
        }
        return errorMsg.substring(0, 1000);
    }
}
