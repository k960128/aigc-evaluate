package com.kant.springai.agent.agent.sales;

import com.kant.springai.agent.common.config.SalesMultiAgentModelProvider;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.util.Assert;

import java.util.Objects;

/**
 * 销售 Worker Agent 公共实现。
 *
 * <p>每个子类拥有独立的 ChatClient、系统角色和工具集合。</p>
 */
abstract class AbstractSalesWorkerAgent implements SalesWorkerAgent {

    private final SalesMultiAgentModelProvider modelProvider;
    private final Object[] tools;

    private volatile ChatClient chatClient;

    protected AbstractSalesWorkerAgent(SalesMultiAgentModelProvider modelProvider,
                                       Object... tools) {
        this.modelProvider = Objects.requireNonNull(modelProvider, "modelProvider 不能为空");
        this.tools = tools.clone();
    }

    protected abstract String systemPrompt();

    @Override
    public String execute(WorkerRequest request) {
        Objects.requireNonNull(request, "WorkerRequest 不能为空");
        Assert.hasText(request.question(), "用户问题不能为空");
        Assert.hasText(request.instruction(), "Supervisor 指令不能为空");

        String userPrompt = """
                【当前日期】
                %s

                【用户原始问题】
                %s

                【Supervisor 分配的任务】
                %s

                【前序 Agent 执行记录】
                %s

                请只完成 Supervisor 分配给你的任务。需要具体业务数据时必须调用你拥有的工具，
                不得编造数据，也不要替 Supervisor 决定还要调用哪些其他 Agent。
                """.formatted(
                request.today(),
                request.question(),
                request.instruction(),
                request.history());

        return getChatClient().prompt()
                .system(systemPrompt())
                .user(userPrompt)
                .call()
                .content();
    }

    private ChatClient getChatClient() {
        ChatClient current = chatClient;
        if (current != null) {
            return current;
        }

        synchronized (this) {
            if (chatClient == null) {
                chatClient = ChatClient.builder(modelProvider.getChatModel())
                        .defaultToolCallbacks(ToolCallbacks.from(tools))
                        .defaultAdvisors(new SimpleLoggerAdvisor())
                        .build();
            }
            return chatClient;
        }
    }
}
