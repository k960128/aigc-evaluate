package com.kant.llm.rag;

import com.kant.llm.eval.dao.entity.ModelInfoDO;
import com.kant.llm.eval.service.ModelInfoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.zhipuai.ZhiPuAiChatModel;
import org.springframework.ai.zhipuai.ZhiPuAiChatOptions;
import org.springframework.ai.zhipuai.api.ZhiPuAiApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@Slf4j
@SpringBootTest
@ActiveProfiles("test")
public class AbstractRagQuery {
    @Autowired
    private ModelInfoService modelInfoService;

    /**
     * 获取ChatClient
     * @return ChatClient
     */
    ChatClient getChatClient() {
        ModelInfoDO modelInfoDO = modelInfoService.getById(3L);
        ChatModel chatModel = new ZhiPuAiChatModel(ZhiPuAiApi.builder()
                .apiKey(modelInfoDO.getApiKey())
                .build(),
                ZhiPuAiChatOptions.builder()
                        .model(modelInfoDO.getModel())
                        .build());
        return ChatClient.builder(chatModel).build();
    }

}
