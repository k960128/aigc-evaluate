package com.kant.agent.simple.tactic;

/**
 * 凯撒位移：仅对 ASCII 可见字符（0x20~0x7E）做位移，位移量固定 3。
 * 中文原样保留（凯撒位移只对英文有意义）。
 */
public class CaesarTransformer implements TacticTransformer {

    private static final int SHIFT = 3;
    private static final int ASCII_MIN = 0x20;
    private static final int ASCII_MAX = 0x7E;
    private static final int RANGE = ASCII_MAX - ASCII_MIN + 1;

    @Override
    public String transformerId() {
        return "caesar";
    }

    @Override
    public String apply(String plainText) {
        return shift(plainText, SHIFT);
    }

    @Override
    public String reverse(String transformedText) {
        return shift(transformedText, -SHIFT);
    }

    private static String shift(String text, int shift) {
        if (text == null) return null;
        StringBuilder sb = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (c >= ASCII_MIN && c <= ASCII_MAX) {
                int shifted = ((c - ASCII_MIN + shift) % RANGE + RANGE) % RANGE + ASCII_MIN;
                sb.append((char) shifted);
            } else {
                sb.append(c);   // 中文原样
            }
        }
        return sb.toString();
    }
}