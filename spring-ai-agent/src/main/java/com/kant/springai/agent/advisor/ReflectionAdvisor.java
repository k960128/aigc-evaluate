package com.kant.springai.agent.advisor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.model.ChatModel;

@Slf4j
public class ReflectionAdvisor implements CallAdvisor {


    /**
     * 反思评估提示
     */
    private static final String REFLECTION_PROMPT = """
            你是一个严格的智能体反思评估专家。
            
            请判断【当前回答】是否已经充分、准确地满足【用户问题】。
            
            评估标准：
            1. 信息是否完整
            2. 逻辑是否清晰
            3. 结论是否可靠、与上下文一致
            4. 表达是否符合最终交付质量
            
            【你必须且只能输出一个 JSON 对象，格式如下】
            
            {
              "passed": true | false,
              "feedback": "如果 passed=false，给出明确、可执行的改进建议，但是必须不要过长，控制在100字以内；如果 passed=true，值为 null"
            }
            
            禁止输出任何额外文本。
            """;

    private final ChatModel reflectionModel;


    public ReflectionAdvisor(ChatModel reflectionModel) {
        this.reflectionModel = reflectionModel;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest chatClientRequest,
                                         CallAdvisorChain callAdvisorChain) {
        return null;
    }

    @Override
    public String getName() {
        return "ReflectionAdvisor";
    }

    @Override
    public int getOrder() {
        return 50;
    }

}
