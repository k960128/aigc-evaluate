package com.kant.agent.agent.impl;

import com.kant.agent.agent.SampleAgent;
import com.kant.agent.contract.AttackSample;
import com.kant.agent.contract.RedTeamEvaluateRequest;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/**
 * 第一阶段的样本智能体：使用配置化固定种子创建可执行样本。
 *
 * <p>当前实现不调用大模型，也不改变种子语义，只负责把请求中的种子封装成统一的
 * {@link AttackSample}。后续可以在不改变 {@link SampleAgent} 接口的情况下增加模型
 * 生成和样本变异实现。</p>
 */
@Service
public class FixedSampleAgent implements SampleAgent {

    /**
     * 创建一个基于固定种子的攻击样本。
     *
     * @param taskId 当前评测任务标识
     * @param request 评测请求，其中的 seedContent 会作为样本正文
     * @return 封装完成的攻击样本
     */
    @Override
    public AttackSample create(String taskId, RedTeamEvaluateRequest request) {
        // 去除种子首尾的空白，避免同一业务内容因为请求格式差异产生不同样本。
        String content = request.seedContent().trim();

        // M1 先使用固定来源和 NONE 变异算子，提前填充字段以兼容后续样本工厂。
        return new AttackSample(
                "SA-" + UUID.randomUUID().toString().substring(0, 8),
                taskId,
                content,
                request.riskCategory(),
                "FIXED",
                null,
                "NONE",
                fingerprint(content)
        );
    }

    /**
     * 计算样本内容指纹。
     *
     * <p>先把连续空白归一化并转为小写，再计算 SHA-256，使仅有大小写或排版差异的
     * 样本能够得到相同指纹。指纹只用于追踪和去重，不作为安全判定依据。</p>
     *
     * @param content 待计算指纹的样本内容
     * @return 十六进制表示的 SHA-256 指纹
     */
    private String fingerprint(String content) {
        // 规范化文本，降低无意义的空白和大小写差异对指纹的影响。
        String normalized = content.replaceAll("\\s+", " ").trim().toLowerCase();
        try {
            // 使用标准库计算稳定的内容摘要，不引入额外的加密依赖。
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(normalized.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 属于 Java 标准算法；若运行环境缺失，继续执行没有可靠意义。
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
