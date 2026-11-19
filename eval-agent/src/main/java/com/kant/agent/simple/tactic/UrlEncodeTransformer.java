package com.kant.agent.simple.tactic;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class UrlEncodeTransformer implements TacticTransformer {

    @Override
    public String transformerId() {
        return "url";
    }

    @Override
    public String apply(String plainText) {
        if (plainText == null) return null;
        return URLEncoder.encode(plainText, StandardCharsets.UTF_8);
    }

    @Override
    public String reverse(String transformedText) {
        if (transformedText == null) return null;
        try {
            return URLDecoder.decode(transformedText, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return transformedText;
        }
    }
}