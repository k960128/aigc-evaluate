package com.kant.agent.simple.tactic;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class Base64Transformer implements TacticTransformer {

    @Override
    public String transformerId() {
        return "base64";
    }

    @Override
    public String apply(String plainText) {
        if (plainText == null) return null;
        return Base64.getEncoder().encodeToString(plainText.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public String reverse(String transformedText) {
        if (transformedText == null) return null;
        try {
            byte[] decoded = Base64.getDecoder().decode(transformedText);
            return new String(decoded, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            // 非法 Base64，原样返回（由上层反解校验判失败）
            return transformedText;
        }
    }
}