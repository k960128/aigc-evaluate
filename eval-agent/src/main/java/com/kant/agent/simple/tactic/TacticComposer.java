package com.kant.agent.simple.tactic;

import com.kant.agent.simple.model.SampleTaskTicket;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 扫描语义骨架中的占位符 {{transformerId:明文}}，替换成程序化密文。
 */
public class TacticComposer {

    /** 占位符正则：{{id:明文}}，明文可含中文/英文/空格，但不含 } */
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{(\\w+):([^}]+)}}");

    private final TacticTransformerRegistry registry;

    public TacticComposer(TacticTransformerRegistry registry) {
        this.registry = registry;
    }

    public ComposedSample compose(String semanticScaffold,
                                  List<SampleTaskTicket.TacticsContent> tactics) {
        if (semanticScaffold == null) {
            return new ComposedSample(null, null, List.of(), List.of(), false);
        }

        List<String> applied = new ArrayList<>();
        boolean reverseVerified = true;

        StringBuilder sb = new StringBuilder();
        Matcher m = PLACEHOLDER.matcher(semanticScaffold);
        while (m.find()) {
            String id = m.group(1);
            String plain = m.group(2);
            try {
                TacticTransformer transformer = registry.get(id);
                String cipher = transformer.apply(plain);

                // 反解校验：只校验"自然语言输入"，Leet 的有损性不判 fail
                if (!id.equals("leet")) {
                    String roundtrip = transformer.reverse(cipher);
                    if (!plain.equals(roundtrip)) {
                        reverseVerified = false;
                    }
                }

                applied.add(id);
                m.appendReplacement(sb, Matcher.quoteReplacement(cipher));
            } catch (IllegalArgumentException e) {
                // 未知 id → 保留原样，标 fail
                reverseVerified = false;
                m.appendReplacement(sb, Matcher.quoteReplacement(m.group(0)));
            }
        }
        m.appendTail(sb);

        // 检查战术覆盖
        List<String> missing = tactics.stream()
                .filter(t -> t.category() == SampleTaskTicket.TacticCategory.TRANSFORMATION)
                .map(SampleTaskTicket.TacticsContent::transformerId)
                .filter(Objects::nonNull)
                .filter(id -> !applied.contains(id))
                .toList();

        return new ComposedSample(sb.toString(), semanticScaffold, applied, missing, reverseVerified);
    }

    /** 仅供测试：统计骨架里有多少个占位符 */
    public int countPlaceholders(String semanticScaffold) {
        if (semanticScaffold == null) return 0;
        Matcher m = PLACEHOLDER.matcher(semanticScaffold);
        int count = 0;
        while (m.find()) count++;
        return count;
    }
}