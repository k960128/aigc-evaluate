package com.kant.agent.simple.tactic;

import java.util.List;

/**
 * Composer 的产物。
 * 注意：semanticScaffold 与输入完全一致（Composer 只派生 finalText，不修改骨架）。
 */
public record ComposedSample(
        String finalText,               // 占位符已替换成密文
        String semanticScaffold,        // 骨架原文（含占位符）
        List<String> appliedTransformers,
        List<String> missingTransformers,   // 战术要求的变换器但骨架里没出现
        boolean reverseVerified             // 所有编码反解一致
) {
    /** 是否可以进入 Judge */
    public boolean isComplete() {
        return missingTransformers.isEmpty() && reverseVerified;
    }

    /** 失败原因（用于 IterationRecord.critique） */
    public String failureReason() {
        if (!missingTransformers.isEmpty()) {
            return "骨架缺少变换战术: " + missingTransformers;
        }
        if (!reverseVerified) {
            return "存在编码不可逆的占位符";
        }
        return null;
    }
}