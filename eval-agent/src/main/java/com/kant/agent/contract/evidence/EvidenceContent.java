package com.kant.agent.contract.evidence;

import com.kant.agent.contract.common.ContractChecks;

/**
 * 通过权限校验后读取到的证据正文。
 *
 * <p>该对象只应由受控证据读取端口返回。普通日志、事件和列表接口不得携带
 * {@code content}。</p>
 *
 * @param reference 证据索引
 * @param content   原始证据正文
 */
public record EvidenceContent(EvidenceReference reference, String content) {

    /**
     * 校验证据索引和正文。
     */
    public EvidenceContent {
        reference = ContractChecks.required(reference, "reference");
        content = ContractChecks.required(content, "content");
    }
}
