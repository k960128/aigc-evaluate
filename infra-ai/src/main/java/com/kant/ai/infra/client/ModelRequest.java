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
public class ModelRequest {


    /**
     * 模型信息
     */
    private ClientModelInfo clientModelInfo;
    /**
     * 输入内容
     */
    private String inputText;

    /**
     * 用户提示词
     */
    List<Message> userMessages;

    List<Message> systemMessages;

    PromptTemplate promptTemplate;

}
