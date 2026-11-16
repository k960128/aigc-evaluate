package com.kant.llm.eval.simple.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.Instant;
import java.util.List;

/**
 * D05控制面请求契约。认证身份由服务端获取，请求不能覆盖租户、操作者或审核人。
 * 字符串长度使用校验注解约束，领域组合约束由嵌套契约构造器校验；不接受目标执行参数。
 */
public final class SimpleSeedFactoryRequests {
    /**
     * 创建不可变专家种子，不触发模型生成。
     * @param idempotencyKey 主体内种子创建键；相同操作重试复用，同键异参冲突
     * @param name 种子展示名，不是样本正文
     * @param content 专家原文，保留首尾空格和换行，不允许空白正文
     * @param sourceRef 可空来源引用，只存储不访问外部地址
     */
    public record Seed(@NotBlank @Size(max = 128) String idempotencyKey, @NotBlank @Size(max = 256) String name,
                       @NotBlank @Size(max = 10000) String content, @Size(max = 256) String sourceRef) {
        /* 种子创建只保存专家资料，不触发Actor、Critic或被测目标调用。 */
    }
}
