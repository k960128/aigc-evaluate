package com.kant.agent.simple.tactic;

import java.nio.charset.StandardCharsets;

public class HexTransformer implements TacticTransformer {

    @Override
    public String transformerId() {
        return "hex";
    }

    @Override
    public String apply(String plainText) {
        if (plainText == null) return null;
        StringBuilder sb = new StringBuilder();
        for (byte b : plainText.getBytes(StandardCharsets.UTF_8)) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    @Override
    public String reverse(String transformedText) {
        if (transformedText == null) return null;
        // 偶数长度 + 全 hex 字符才可能是合法 Hex
        if (transformedText.length() % 2 != 0
                || !transformedText.matches("[0-9a-fA-F]+")) {
            return transformedText;
        }
        byte[] bytes = new byte[transformedText.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) Integer.parseInt(
                    transformedText.substring(i * 2, i * 2 + 2), 16);
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }
}