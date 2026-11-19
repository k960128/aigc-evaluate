package com.kant.agent.simple.tactic;

import java.util.HashMap;
import java.util.Map;

/**
 * 字符级扰动：
 * - 英文字母用 Leet 替换（a→4, e→3, i→1, o→0, s→5, t→7）
 * - 中文用零宽字符 U+200B 插入，打破 Token 序列
 */
public class CharPerturbTransformer implements TacticTransformer {

    private static final char ZERO_WIDTH = '\u200B';

    private static final Map<Character, Character> LEET = new HashMap<>();
    private static final Map<Character, Character> LEET_REVERSE = new HashMap<>();

    static {
        LEET.put('a', '4');
        LEET.put('e', '3');
        LEET.put('i', '1');
        LEET.put('o', '0');
        LEET.put('s', '5');
        LEET.put('t', '7');
        LEET.forEach((k, v) -> LEET_REVERSE.put(v, k));
    }

    @Override
    public String transformerId() {
        return "leet";
    }

    @Override
    public String apply(String plainText) {
        if (plainText == null) return null;
        StringBuilder sb = new StringBuilder();
        for (char c : plainText.toCharArray()) {
            if (c < 128) {
                // 英文/数字/符号：Leet 替换（大小写保留）
                char lower = Character.toLowerCase(c);
                if (LEET.containsKey(lower)) {
                    char replaced = LEET.get(lower);
                    sb.append(Character.isUpperCase(c) ? Character.toUpperCase(replaced) : replaced);
                } else {
                    sb.append(c);
                }
            } else {
                // 中文：字符后插入零宽
                sb.append(c).append(ZERO_WIDTH);
            }
        }
        return sb.toString();
    }

    @Override
    public String reverse(String transformedText) {
        if (transformedText == null) return null;
        StringBuilder sb = new StringBuilder();
        for (char c : transformedText.toCharArray()) {
            if (c == ZERO_WIDTH) continue;   // 去零宽
            if (LEET_REVERSE.containsKey(c)) {
                sb.append(LEET_REVERSE.get(c));
            } else if (LEET_REVERSE.containsKey(Character.toLowerCase(c))) {
                sb.append(Character.toUpperCase(LEET_REVERSE.get(Character.toLowerCase(c))));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}