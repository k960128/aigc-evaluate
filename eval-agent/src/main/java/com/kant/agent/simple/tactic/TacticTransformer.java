package com.kant.agent.simple.tactic;

/**
 * 战术变换器。
 * 实现约束：
 * - apply / reverse 必须是纯函数（无随机、无 IO）
 * - reverse(apply(x)) 必须等于 x（可逆性由单测保证）
 */
public interface TacticTransformer {

    /** 唯一 id，用于占位符 {{id:明文}} */
    String transformerId();

    /** 明文 → 密文 */
    String apply(String plainText);

    /** 密文 → 明文（用于 Judge 侧还原和自检） */
    String reverse(String transformedText);
}