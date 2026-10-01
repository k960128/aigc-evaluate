package com.kant.agent.port.target;

import com.kant.agent.contract.target.TargetReference;
import com.kant.agent.contract.target.TargetSnapshot;

/**
 * 查询被测目标快照和真实能力的业务端口。
 */
public interface TargetCapabilityPort {

    /**
     * 读取当前目标配置并生成不含明文凭据的快照。
     *
     * @param tenantId 当前租户或业务空间
     * @param actorId  当前操作者
     * @param target   目标资源引用
     * @return 目标快照
     */
    TargetSnapshot getSnapshot(String tenantId, String actorId, TargetReference target);
}
