package com.kant.agent.simple.tactic;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public class TacticTransformerRegistry {

    private final Map<String, TacticTransformer> transformers;

    public TacticTransformerRegistry(Collection<TacticTransformer> transformerList) {
        this.transformers = transformerList.stream()
                .collect(Collectors.toUnmodifiableMap(
                        TacticTransformer::transformerId, Function.identity()));
    }

    /** 默认工厂：注册 5 个内置变换器 */
    public static TacticTransformerRegistry createDefault() {
        return new TacticTransformerRegistry(List.of(
                new Base64Transformer(),
                new HexTransformer(),
                new CharPerturbTransformer(),
                new UrlEncodeTransformer(),
                new CaesarTransformer()
        ));
    }

    public TacticTransformer get(String id) {
        TacticTransformer t = transformers.get(id);
        if (t == null) {
            throw new IllegalArgumentException(STR."未知 transformer: \{id}，已注册: \{transformers.keySet()}");
        }
        return t;
    }

    public boolean contains(String id) {
        return transformers.containsKey(id);
    }

    public Set<String> allIds() {
        return transformers.keySet();
    }
}