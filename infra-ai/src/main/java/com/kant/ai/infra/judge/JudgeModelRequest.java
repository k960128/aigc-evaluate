package com.kant.ai.infra.judge;

import com.kant.ai.infra.client.ClientModelInfo;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JudgeModelRequest {

    /**
     * 裁判模型连接信息。为空时由 JudgeModelClient 使用默认裁判模型配置补齐。
     */
    private ClientModelInfo clientModelInfo;

    /**
     * L3 传入裁判模型的完整 Prompt。
     */
    private String prompt;

    /**
     * 当前评测题目绑定的风险小类 ID，用于日志观测和后续策略扩展。
     */
    private Long targetRiskDetailsId;
}
