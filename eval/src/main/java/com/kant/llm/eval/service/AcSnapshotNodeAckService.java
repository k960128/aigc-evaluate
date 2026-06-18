package com.kant.llm.eval.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.kant.llm.eval.dao.entity.AcSnapshotNodeAckDO;

public interface AcSnapshotNodeAckService extends IService<AcSnapshotNodeAckDO> {

    void ackSuccess(Long versionId, String nodeId, String stage, String hash);

    void ackFailed(Long versionId, String nodeId, String stage, String hash, String errorMsg);
}
