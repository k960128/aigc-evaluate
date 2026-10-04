package com.kant.agent.service.production;

import com.kant.agent.contract.production.GenerationSpec;
import com.kant.agent.port.production.SampleGenerationPort;

import java.text.Normalizer;
import java.util.*;

/**
 * 首版确定性质检：根据生成分类、正文长度、主题锚点和控制字符做本地判断，不调用模型。
 * PASS只是基础规则通过，不是语义相关性、完整隐私检测或目标安全验证。
 * 去重唯一性由持久化层保证，正式准入仍需人工审核。
 */
public final class SampleQualityWorkflow {
    /** 质量规则版本，随报告保存，审核时核对兼容性。 */
    public static final String VERSION = "sample-quality-v1";
    /** 去重算法版本，参与数据库唯一索引，不与原文哈希混用。 */
    public static final String DEDUP_VERSION = "nfkc-whitespace-lowercase-v1";

    /**
     * 不可变质量报告，也是有限改写的反馈资料。
     * @param version 检查器版本，不是候选审核版本
     * @param outcome PASS通过、REVISE可改写、REJECT拒绝；能否改写还受外层预算控制
     * @param issues 质量问题列表，PASS通常为空，不是目标风险证据
     */
    public record Report(String version, String outcome, List<String> issues) {
        /** 防御性复制，避免质量意见被外部列表修改。 */
        public Report {
            issues = List.copyOf(issues);
        }
    }

    /**
     * 拒绝/非法结构/空文直接REJECT，长度或锚点缺失为REVISE，非法控制字符优先拒绝。
     * @param spec 专家冻结的长度与主题约束
     * @param result 已确认模型结果，不是网络未知状态
     * @return 基础质量报告，不负责扣预算、写库或实际调用改写
     */
    public Report inspect(GenerationSpec spec, SampleGenerationPort.Result result) {
        if ("REFUSED".equals(result.status()))
            return new Report(VERSION, "REJECT", List.of("生成模型拒绝，不执行拒绝绕过或补足通过数量"));
        if (!"GENERATED".equals(result.status()))
            return new Report(VERSION, "REJECT", List.of("生成模型未返回合法结构化样本"));
        String content = result.content();
        if (content == null || content.isBlank()) return new Report(VERSION, "REJECT", List.of("候选正文为空"));
        var issues = new ArrayList<String>();
        if (content.length() < spec.minLength() || content.length() > spec.maxLength())
            issues.add("正文长度不满足专家配置");
        // 仅做不区分大小写的字面包含判断，至少命中一个锚点；没有调用语义模型。
        String folded = content.toLowerCase(Locale.ROOT);
        if (spec.topicTerms().stream().noneMatch(s -> folded.contains(s.toLowerCase(Locale.ROOT))))
            issues.add("未命中任何主题锚点，需要复核相关性");
        if (content.codePoints().anyMatch(c -> Character.isISOControl(c) && c != '\n' && c != '\r' && c != '\t'))
            return new Report(VERSION, "REJECT", List.of("正文包含不支持的控制字符"));
        return new Report(VERSION, issues.isEmpty() ? "PASS" : "REVISE", issues);
    }

    /**
     * 依次执行NFKC、Java默认空白合并、trim和Locale.ROOT小写，供持久化层计算摘要。
     * 只生成去重键，不改写原候选，不做语义相似判断，也不是完整Unicode case folding。
     */
    public String normalized(String content) {
        return Normalizer.normalize(content, Normalizer.Form.NFKC).replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
    }
}
