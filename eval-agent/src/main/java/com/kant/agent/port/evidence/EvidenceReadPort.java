package com.kant.agent.port.evidence;

import com.kant.agent.contract.evidence.EvidenceContent;
import com.kant.agent.contract.evidence.EvidenceQuery;

import java.util.Optional;

/**
 * 受权限控制的原始证据读取端口。
 */
public interface EvidenceReadPort {

    /**
     * 读取指定执行所属的证据正文。
     *
     * @param query 带租户、操作者和所属执行约束的查询
     * @return 允许访问且存在时返回证据
     */
    Optional<EvidenceContent> read(EvidenceQuery query);
}
