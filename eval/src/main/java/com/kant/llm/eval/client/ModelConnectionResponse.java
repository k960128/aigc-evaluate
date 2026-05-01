package com.kant.llm.eval.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelConnectionResponse {
    /**
     * 连通性测试结果
     * true：成功
     * false：失败
     */
    private Boolean result;

    /**
     * 大模型返回的内容
     */
    private String respContent;
    /**
     * 接口耗时
     */
    private Long elapsed;
}
