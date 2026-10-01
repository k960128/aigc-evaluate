package com.kant.agent.contract.production;

import com.kant.agent.contract.common.ContractChecks;
import java.util.List;

/**
 * 样本生产的不可变配置。与目标评测分离，不含targetId或目标调用额度。
 * @param seedId 原专家种子
 * @param seedVersion 精确种子版本，当前种子只追加创建不覆盖
 * @param generatorProfileRef 生成模型配置快照，不是被测目标快照
 * @param promptVersion 生成/改写提示词版本
 * @param riskId 样本所属风险目录编码，不表示该风险已在目标上出现
 * @param ruleSetVersion 未来评测采用的规则版本
 * @param quantity 根样本槽位数；低质量不强行补足通过数量
 * @param mutations 专家允许的变异要求，作为数据而非平台工具指令
 * @param topicTerms 确定性质检的主题锚点，至少命中一项；不是语义相关性的完整证明
 * @param minLength 最小正文字符数
 * @param maxLength 最大正文字符数
 * @param maxRewrites 每个槽位最多质量改写次数
 * @param preconditions 专家提供的可核对前置条件
 * @param assertionDescription 专家填写的评测断言说明，未经目标执行不生成安全结论
 */
public record GenerationSpec(String seedId,long seedVersion,String generatorProfileRef,String promptVersion,
        String riskId,String ruleSetVersion,int quantity,List<String> mutations,List<String> topicTerms,
        int minLength,int maxLength,int maxRewrites,String preconditions,String assertionDescription) {
    public GenerationSpec {
        seedId=ContractChecks.maxLength(ContractChecks.notBlank(seedId,"seedId"),96,"seedId"); ContractChecks.require(seedVersion==1,"当前种子只支持不可变版本1");
        generatorProfileRef=ContractChecks.maxLength(ContractChecks.notBlank(generatorProfileRef,"generatorProfileRef"),96,"generatorProfileRef");
        ContractChecks.require("sample-generation-v1".equals(promptVersion),"未支持的样本生成提示词版本");
        riskId=ContractChecks.maxLength(ContractChecks.notBlank(riskId,"riskId"),128,"riskId");
        ruleSetVersion=ContractChecks.maxLength(ContractChecks.notBlank(ruleSetVersion,"ruleSetVersion"),128,"ruleSetVersion");
        ContractChecks.require(quantity>=1 && quantity<=500,"每批根样本数量为1至500");
        mutations=List.copyOf(ContractChecks.notEmpty(mutations,"mutations")); topicTerms=List.copyOf(ContractChecks.notEmpty(topicTerms,"topicTerms"));
        ContractChecks.require(mutations.size()<=10 && topicTerms.size()<=20,"变异要求或主题锚点过多");
        mutations.forEach(s -> ContractChecks.maxLength(ContractChecks.notBlankPreserving(s,"mutation"),500,"mutation"));
        topicTerms.forEach(s -> ContractChecks.maxLength(ContractChecks.notBlank(s,"topicTerm"),128,"topicTerm"));
        ContractChecks.require(minLength>=1 && maxLength>=minLength && maxLength<=10000,"正文长度范围不合法");
        ContractChecks.require(maxRewrites>=0 && maxRewrites<=2,"每个样本最多两次质量改写");
        ContractChecks.maxLength(ContractChecks.notBlankPreserving(preconditions,"preconditions"),4000,"preconditions");
        ContractChecks.maxLength(ContractChecks.notBlankPreserving(assertionDescription,"assertionDescription"),4000,"assertionDescription");
    }
}
