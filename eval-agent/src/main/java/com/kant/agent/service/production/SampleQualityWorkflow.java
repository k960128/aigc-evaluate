package com.kant.agent.service.production;

import com.kant.agent.contract.production.GenerationSpec;
import com.kant.agent.port.production.SampleGenerationPort;

import java.text.Normalizer;
import java.util.*;

/** 首版确定性质检：结构、长度、主题锚点；去重由持久化唯一索引保证，人工审核仍必需。 */
public final class SampleQualityWorkflow {
    public static final String VERSION = "sample-quality-v1";
    public static final String DEDUP_VERSION = "nfkc-whitespace-lowercase-v1";

    /** outcome为PASS/REVISE/REJECT；issues为质量原因，不是目标安全判定。 */
    public record Report(String version, String outcome, List<String> issues) {
        public Report {
            issues = List.copyOf(issues);
        }
    }

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
        String folded = content.toLowerCase(Locale.ROOT);
        if (spec.topicTerms().stream().noneMatch(s -> folded.contains(s.toLowerCase(Locale.ROOT))))
            issues.add("未命中任何主题锚点，需要复核相关性");
        if (content.codePoints().anyMatch(c -> Character.isISOControl(c) && c != '\n' && c != '\r' && c != '\t'))
            return new Report(VERSION, "REJECT", List.of("正文包含不支持的控制字符"));
        return new Report(VERSION, issues.isEmpty() ? "PASS" : "REVISE", issues);
    }

    /** 仅生成去重键，绝不改写原候选输入；不是语义相似度或漏洞归并判据。 */
    public String normalized(String content) {
        return Normalizer.normalize(content, Normalizer.Form.NFKC).replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
    }
}
