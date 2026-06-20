package com.kant.llm.eval.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kant.llm.eval.knowledge.dao.entity.AcSnapshotDirtyShardDO;
import com.kant.llm.eval.knowledge.dao.mapper.AcSnapshotDirtyShardMapper;
import com.kant.llm.eval.knowledge.service.AcSnapshotDirtyShardService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class AcSnapshotDirtyShardServiceImpl
        extends ServiceImpl<AcSnapshotDirtyShardMapper, AcSnapshotDirtyShardDO>
        implements AcSnapshotDirtyShardService {

    @Override
    public void markDirty(Long riskDetailsId, String reason) {
        if (riskDetailsId == null) {
            return;
        }
        AcSnapshotDirtyShardDO existing = getOne(new LambdaQueryWrapper<AcSnapshotDirtyShardDO>()
                .eq(AcSnapshotDirtyShardDO::getRiskDetailsId, riskDetailsId)
                .last("LIMIT 1"));
        if (existing == null) {
            existing = new AcSnapshotDirtyShardDO();
            existing.setRiskDetailsId(riskDetailsId);
            existing.setDirtyReason(reason);
            existing.setLastChangeTime(LocalDateTime.now());
            save(existing);
            return;
        }
        existing.setDirtyReason(reason);
        existing.setLastChangeTime(LocalDateTime.now());
        updateById(existing);
    }

    @Override
    public void clearAll() {
        remove(new LambdaQueryWrapper<AcSnapshotDirtyShardDO>()
                .isNotNull(AcSnapshotDirtyShardDO::getId));
    }
}
