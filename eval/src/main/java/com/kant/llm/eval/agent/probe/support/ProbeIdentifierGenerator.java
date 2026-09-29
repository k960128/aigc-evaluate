package com.kant.llm.eval.agent.probe.support;

import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * D01 探测执行相关标识生成器。
 *
 * <p>所有标识均为不含短横线的随机 UUID，并使用前缀区分业务对象，便于
 * 日志检索和人工排查。数据库唯一索引负责最终碰撞保护。</p>
 */
@Component
public class ProbeIdentifierGenerator {

    /** 生成物理执行标识。 */
    public String executionId() {
        return generate("pex_");
    }

    /** 生成输入证据标识。 */
    public String inputEvidenceRef() {
        return generate("evi_in_");
    }

    /** 生成目标响应证据标识。 */
    public String responseEvidenceRef() {
        return generate("evi_out_");
    }

    /** 生成目标错误证据标识。 */
    public String errorEvidenceRef() {
        return generate("evi_err_");
    }

    private String generate(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "");
    }
}
