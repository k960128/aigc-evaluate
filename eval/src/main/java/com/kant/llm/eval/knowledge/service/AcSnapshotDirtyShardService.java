package com.kant.llm.eval.knowledge.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.kant.llm.eval.knowledge.dao.entity.AcSnapshotDirtyShardDO;

public interface AcSnapshotDirtyShardService extends IService<AcSnapshotDirtyShardDO> {

    void markDirty(Long riskDetailsId, String reason);

    void clearAll();
}
