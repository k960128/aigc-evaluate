package com.kant.llm.eval.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelRequest {


    /**
     * 模型信息
     */
    private ModelInfo modelInfo;
    /**
     * 输入内容
     */
    private String inputText;
}
