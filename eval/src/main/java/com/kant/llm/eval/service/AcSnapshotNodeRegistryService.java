package com.kant.llm.eval.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.kant.llm.eval.dao.entity.AcSnapshotNodeRegistryDO;

import java.util.List;

public interface AcSnapshotNodeRegistryService extends IService<AcSnapshotNodeRegistryDO> {

    void heartbeat(String nodeId, String appName, String host, Long currentVersion, Long preparedVersion);

    List<AcSnapshotNodeRegistryDO> listActiveNodes(int heartbeatTimeoutSeconds);
}
