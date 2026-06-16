package com.kant.ai.infra.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.prompt.PromptTemplate;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MockModelRequest {


    /**
     * 模型信息
     */
    private ClientModelInfo modelInfo;
    /**
     * 输入内容
     */
    private String inputText;

    List<Message> userMessages;

    List<Message> systemMessages;

    PromptTemplate promptTemplate;

    private String modelOutput;
    /**
     * 样本ID
     */
    private Long sampleId;
}
