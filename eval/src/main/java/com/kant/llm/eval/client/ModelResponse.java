package com.kant.llm.eval.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelResponse {

    /**
     * 大模型ID
     */
    private Long modelId;
    /**
     * 大模型返回的内容
     */
    private String respContent;
    /**
     * 接口耗时
     */
    private Long elapsed;
}
