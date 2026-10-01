package com.kant.agent.contract.finding;

import com.kant.agent.contract.common.ContractChecks;
import com.kant.agent.contract.evidence.EvidenceReference;
import com.kant.agent.contract.evidence.EvidenceType;

/**
 * 一轮复核时读取到的证据索引快照，不复制敏感原文。
 * @param sequence 原运行动作序号
 * @param executionId 真实物理执行标识
 * @param input 已核对的输入索引、归属和正文哈希
 * @param response 已核对的响应索引、归属和正文哈希
 */
public record FindingEvidenceSnapshot(int sequence,String executionId,EvidenceReference input,EvidenceReference response) {
    public FindingEvidenceSnapshot {
        ContractChecks.positive(sequence,"sequence");
        executionId=ContractChecks.notBlank(executionId,"executionId");
        input=ContractChecks.required(input,"input"); response=ContractChecks.required(response,"response");
        ContractChecks.require(input.type()==EvidenceType.PROBE_INPUT && response.type()==EvidenceType.TARGET_RESPONSE,
                "快照必须保存成对的输入和响应索引");
        ContractChecks.require("PROBE_EXECUTION".equals(input.ownerType()) && "PROBE_EXECUTION".equals(response.ownerType())
                && executionId.equals(input.ownerRef()) && executionId.equals(response.ownerRef()),"证据必须归属当前物理执行");
    }
}
