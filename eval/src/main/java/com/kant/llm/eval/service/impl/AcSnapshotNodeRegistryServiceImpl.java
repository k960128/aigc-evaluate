package com.kant.llm.eval.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kant.llm.eval.common.enums.AcSnapshotNodeStatusEnums;
import com.kant.llm.eval.dao.entity.AcSnapshotNodeRegistryDO;
import com.kant.llm.eval.dao.mapper.AcSnapshotNodeRegistryMapper;
import com.kant.llm.eval.service.AcSnapshotNodeRegistryService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AcSnapshotNodeRegistryServiceImpl
        extends ServiceImpl<AcSnapshotNodeRegistryMapper, AcSnapshotNodeRegistryDO>
        implements AcSnapshotNodeRegistryService {

    @Override
    public void heartbeat(String nodeId, String appName, String host, Long currentVersion, Long preparedVersion) {
        LocalDateTime now = LocalDateTime.now();
        AcSnapshotNodeRegistryDO existing = getOne(new LambdaQueryWrapper<AcSnapshotNodeRegistryDO>()
                .eq(AcSnapshotNodeRegistryDO::getNodeId, nodeId)
                .last("LIMIT 1"));
        if (existing == null) {
            existing = new AcSnapshotNodeRegistryDO();
            existing.setNodeId(nodeId);
            existing.setAppName(appName);
            existing.setHost(host);
            existing.setLastHeartbeatTime(now);
            existing.setCurrentVersion(currentVersion);
            existing.setPreparedVersion(preparedVersion);
            existing.setStatus(AcSnapshotNodeStatusEnums.ONLINE.getCode());
            save(existing);
            return;
        }
        existing.setAppName(appName);
        existing.setHost(host);
        existing.setLastHeartbeatTime(now);
        existing.setCurrentVersion(currentVersion);
        existing.setPreparedVersion(preparedVersion);
        existing.setStatus(AcSnapshotNodeStatusEnums.ONLINE.getCode());
        updateById(existing);
    }

    @Override
    public List<AcSnapshotNodeRegistryDO> listActiveNodes(int heartbeatTimeoutSeconds) {
        LocalDateTime threshold = LocalDateTime.now().minusSeconds(heartbeatTimeoutSeconds);
        return list(new LambdaQueryWrapper<AcSnapshotNodeRegistryDO>()
                .eq(AcSnapshotNodeRegistryDO::getStatus, AcSnapshotNodeStatusEnums.ONLINE.getCode())
                .ge(AcSnapshotNodeRegistryDO::getLastHeartbeatTime, threshold));
    }
}
