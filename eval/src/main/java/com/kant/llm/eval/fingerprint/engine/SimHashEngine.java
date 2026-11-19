package com.kant.llm.eval.fingerprint.engine;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import com.google.common.hash.Hashing;
import com.hankcs.hanlp.HanLP;
import com.hankcs.hanlp.seg.common.Term;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
@Component
public class SimHashEngine {
    /**
     * 计算文本的 64 位 SimHash 指纹
     */
    public long compute(String text) {
        if (text == null || text.trim().isEmpty()) {
            return 0L;
        }

        int[] v = new int[64];
        // 1. 使用 HanLP 标准分词（底层会自动附带词性标注）
        List<Term> terms = HanLP.segment(text);

        for (Term term : terms) {
            String word = term.word;
            String nature = term.nature != null ? term.nature.toString() : "";

            // 2. 核心提纯：直接剔除无意义的表述杂质
            // w:标点符号, u:助词(的/地/得), d:副词(很/绝对/非常), y:语气词(啊/呢/吧)
            if (nature.startsWith("w") || nature.startsWith("u") ||
                    nature.startsWith("d") || nature.startsWith("y")) {
                continue;
            }

            // 3. 计算保留核心词的特征向量
            long wordHash = Hashing.murmur3_128()
                    .hashString(word, StandardCharsets.UTF_8)
                    .asLong();

            int weight = word.length();

            for (int i = 0; i < 64; i++) {
                if (((wordHash >>> i) & 1) == 1) {
                    v[i] += weight;
                } else {
                    v[i] -= weight;
                }
            }
        }

        long fingerprint = 0L;
        for (int i = 0; i < 64; i++) {
            if (v[i] > 0) {
                fingerprint |= (1L << i);
            }
        }
        return fingerprint;
    }

    /**
     * 计算海明距离 (异或后统计二进制 1 的个数)
     */
    public int hammingDistance(long hash1, long hash2) {
        return Long.bitCount(hash1 ^ hash2);
    }
}
