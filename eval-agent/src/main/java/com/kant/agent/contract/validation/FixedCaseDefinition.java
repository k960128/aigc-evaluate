package com.kant.agent.contract.validation;

import com.kant.agent.contract.common.ContractChecks;
import com.kant.agent.contract.target.TargetReference;
import java.util.List;

/**
 * 经审核冻结的MODEL回归用例；不把生成输入或当前规则混入固定轨迹。
 * @param sourceFindingId 所属归并发现
 * @param sourceOccurrenceId 选定的原始发生记录
 * @param target 原目标登记对象；首版修复测试仍使用同一MODEL登记
 * @param baselineSnapshotRef 修复前快照
 * @param environment 原环境，未知时必须由人工明确接受局限
 * @param identityRef 原目标测试凭据引用，不包含凭据
 * @param riskId 原风险编码
 * @param ruleSetVersion 原规则版本，不随目标修复改变
 * @param conversation 是否使用真实多轮上下文
 * @param preconditions 人工可核对的环境/虚构数据/身份准备说明
 * @param steps 冻结输入引用与SHA-256；不在用例正文复制受限输入
 * @param assertionVersion 文本信号断言实现版本
 * @param riskIndicators 从原任务继承的文本信号，审核时明确接受其局限
 */
public record FixedCaseDefinition(String sourceFindingId,String sourceOccurrenceId,TargetReference target,
        String baselineSnapshotRef,String environment,String identityRef,String riskId,String ruleSetVersion,
        boolean conversation,String preconditions,List<Step> steps,String assertionVersion,List<String> riskIndicators) {
    public FixedCaseDefinition {
        ContractChecks.notBlank(sourceFindingId,"sourceFindingId"); ContractChecks.notBlank(sourceOccurrenceId,"sourceOccurrenceId");
        ContractChecks.required(target,"target"); ContractChecks.notBlank(baselineSnapshotRef,"baselineSnapshotRef");
        ContractChecks.notBlank(environment,"environment"); ContractChecks.notBlank(identityRef,"identityRef");
        ContractChecks.notBlank(riskId,"riskId"); ContractChecks.notBlank(ruleSetVersion,"ruleSetVersion");
        preconditions=ContractChecks.maxLength(ContractChecks.notBlank(preconditions,"preconditions"),4000,"preconditions");
        steps=List.copyOf(ContractChecks.notEmpty(steps,"steps"));
        riskIndicators=List.copyOf(ContractChecks.notEmpty(riskIndicators,"riskIndicators"));
        ContractChecks.require(target.targetType()==com.kant.agent.contract.target.TargetType.MODEL,"当前固定验证只支持MODEL");
        riskIndicators.forEach(s -> ContractChecks.notBlank(s,"riskIndicators"));
        ContractChecks.require(steps.size()<=100 && (conversation || steps.size()==1),"仅支持最多100轮的线性MODEL用例");
        for(int i=0;i<steps.size();i++) ContractChecks.require(steps.get(i).sequence()==i+1,"用例步骤必须连续有序");
        ContractChecks.require("literal-response-signal-v1".equals(assertionVersion),"未支持的断言类型");
    }
    /**
     * @param sequence 从1开始的固定输入步骤
     * @param inputExecutionId 原始输入所属执行
     * @param inputEvidenceRef 原始输入引用
     * @param inputHash 固定原文SHA-256，运行前必须校验
     */
    public record Step(int sequence,String inputExecutionId,String inputEvidenceRef,String inputHash) {
        public Step {
            ContractChecks.positive(sequence,"sequence"); ContractChecks.notBlank(inputExecutionId,"inputExecutionId");
            ContractChecks.notBlank(inputEvidenceRef,"inputEvidenceRef"); ContractChecks.notBlank(inputHash,"inputHash");
        }
    }
}
