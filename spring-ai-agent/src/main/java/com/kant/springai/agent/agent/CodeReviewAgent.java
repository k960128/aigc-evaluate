package com.kant.springai.agent.agent;

import com.kant.springai.agent.common.config.RedisChatMemoryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * 代码审查并行工作流的智能体
 * 它是 Parallelization Workflow 实现。
 */
@Slf4j
@Component
public class CodeReviewAgent {

    private final RedisChatMemoryRepository redisChatMemoryRepository;

    public CodeReviewAgent(RedisChatMemoryRepository redisChatMemoryRepository) {
        this.redisChatMemoryRepository = redisChatMemoryRepository;
    }

    /**
     * 默认并发线程数。
     */
    private static final int DEFAULT_WORKERS = 5;

    /**
     * 代码审查维度输入。
     * 每个输入都会被并行处理。
     */
    private static final List<String> REVIEW_INPUTS = List.of(
            """
                    审查维度：安全风险审查
                    
                    重点关注：
                    - 是否存在 SQL 注入风险；
                    - 是否存在命令注入风险；
                    - 是否存在敏感信息泄露；
                    - 是否存在越权访问风险；
                    - 是否存在不安全的反序列化；
                    - 是否存在输入参数未校验问题。
                    """,

            """
                    审查维度：性能问题审查
                    
                    重点关注：
                    - 是否存在循环中频繁访问数据库；
                    - 是否存在不必要的对象创建；
                    - 是否存在低效集合操作；
                    - 是否存在重复计算；
                    - 是否存在阻塞调用；
                    - 是否存在可能导致内存压力的问题。
                    """,

            """
                    审查维度：代码规范审查
                    
                    重点关注：
                    - 命名是否清晰；
                    - 方法职责是否单一；
                    - 代码格式是否规范；
                    - 是否存在魔法值；
                    - 是否存在重复代码；
                    - 是否符合常见 Java 编码习惯。
                    """,

            """
                    审查维度：异常处理审查
                    
                    重点关注：
                    - 是否吞掉异常；
                    - 是否只打印异常但没有处理；
                    - 是否存在空指针风险；
                    - 是否存在资源未关闭问题；
                    - 是否存在错误的异常类型；
                    - 是否缺少必要的边界判断。
                    """,

            """
                    审查维度：可维护性审查
                    
                    重点关注：
                    - 代码是否容易理解；
                    - 逻辑是否过于复杂；
                    - 是否存在过长方法；
                    - 是否存在高耦合；
                    - 是否方便扩展；
                    - 是否方便单元测试。
                    """
    );


    /**
     * 执行代码质量并行审查。
     * @param codeContent 待审查的 Java 代码
     * @return 最终代码质量审查报告
     */
    public String review(String codeContent) {
        return review(codeContent, DEFAULT_WORKERS);
    }

    /**
     * 执行代码质量并行审查。
     * @param codeContent 待审查的 Java 代码
     * @param nWorkers    并发线程数
     * @return 最终代码质量审查报告
     */
    public String review(String codeContent, int nWorkers) {
        // 1、构造统一 systemPrompt，每个并行审查任务都会使用。
        String systemPrompt = String.format("""
                你是一名资深 Java 代码审查专家。
                
                请根据输入中的【审查维度】，只从该维度审查下面这段 Java 代码。
                
                要求：
                - 只分析当前输入指定的审查维度；
                - 不要分析其他维度；
                - 问题要具体，不要泛泛而谈；
                - 如果没有发现明显问题，请明确说明；
                - 输出 Markdown 格式；
                - 输出内容要包含：问题说明、严重程度、修改建议。
                
                【待审查 Java 代码】
                ```java
                %s
                ```
                """, codeContent);

        // 2、并行处理多个审查维度。
        List<String> parallelResults = parallel(systemPrompt, nWorkers);

        // 3、打印每个维度的审查结果，方便观察并行工作流执行过程。
        for (int i = 0; i < REVIEW_INPUTS.size(); i++) {
            log.info("\n并行审查任务 {}：\n输入：{}\n输出：{}", i + 1,
                    REVIEW_INPUTS.get(i), parallelResults.get(i));
        }

        // 4、汇总多个并行审查结果。
        return summarize(codeContent, parallelResults);
    }

    /**
     * 并行处理多个输入。
     * @param systemPrompt 统一系统 Prompt 模板
     * @param nWorkers 并发线程数
     * @return 与 inputs 顺序一致的模型输出结果
     */
    private List<String> parallel(String systemPrompt, int nWorkers) {
        // 1、创建固定线程池，用 nWorkers 控制并发数量。
        try (ExecutorService executor = Executors.newFixedThreadPool(nWorkers);) {
            // 2、把每个 input 包装成一个异步任务。
            List<CompletableFuture<String>> futures = CodeReviewAgent.REVIEW_INPUTS.stream()
                    .map(input -> CompletableFuture.supplyAsync(() -> {
                        try {
                            // 3、每个任务使用同一个 systemPrompt，只是追加不同的 input。
                            return sparkChatClient().prompt(systemPrompt + "\nInput: " + input)
                                    .call().content();
                        } catch (Exception e) {
                            // 4、如果某个输入处理失败，抛出异常，方便定位具体失败输入。
                            throw new RuntimeException("处理异常: " + input, e);
                        }
                    }, executor)).toList();

            // 5、等待所有并行任务完成。
            CompletableFuture<Void> allFutures = CompletableFuture.allOf(
                    futures.toArray(CompletableFuture[]::new));
            allFutures.join();

            // 6、按 futures 原始顺序收集结果。
            return futures.stream().map(CompletableFuture::join)
                    .collect(Collectors.toList());

        }
    }

    /**
     * 汇总多个并行审查结果。
     * @param codeContent     原始 Java 代码
     * @param parallelResults 并行审查结果
     * @return 最终代码质量审查报告
     */
    private String summarize(String codeContent, List<String> parallelResults) {
        // 1、拼接多个并行审查结果。
        StringBuilder context = new StringBuilder();

        for (int i = 0; i < parallelResults.size(); i++) {
            context.append("## 审查任务 ").append(i + 1).append("\n\n");
            context.append("### 审查维度\n").append(REVIEW_INPUTS.get(i)).append("\n\n");
            context.append("### 审查结果\n").append(parallelResults.get(i)).append("\n\n");
        }

        // 2、构造汇总 Prompt。
        String prompt = String.format("""
                你是一名资深 Java 技术专家。
                
                下面是一段 Java 代码的多个并行审查结果。
                请你根据这些结果，生成最终代码质量审查报告。
                
                要求：
                - 不要重新审查代码；
                - 只基于已有并行审查结果进行汇总；
                - 合并重复问题；
                - 按严重程度排序；
                - 给出明确修改建议；
                - 输出 Markdown 格式。
                
                最终报告格式：
                
                # 代码质量审查报告
                
                | 维度 | 问题摘要 | 严重程度 | 修改建议 |
                |:--|:--|:--|:--|
                | 安全风险 | xxx | 高/中/低 | xxx |
                | 性能问题 | xxx | 高/中/低 | xxx |
                | 代码规范 | xxx | 高/中/低 | xxx |
                | 异常处理 | xxx | 高/中/低 | xxx |
                | 可维护性 | xxx | 高/中/低 | xxx |
                
                ## 总体结论
                
                用 100 字以内总结这段代码的整体质量，并说明是否建议合并到主分支。
                
                【原始 Java 代码】
                ```java
                %s
                ```
                
                【并行审查结果】
                %s
                """, codeContent, context);

        // 3、调用大模型生成最终汇总报告。
        return sparkChatClient().prompt(prompt).call().content();
    }



    private ChatClient sparkChatClient() {
        MessageWindowChatMemory chatMemory = MessageWindowChatMemory.builder().maxMessages(10)
                .chatMemoryRepository(redisChatMemoryRepository).build();
        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(OpenAiApi.builder()
                        .apiKey("QflVuNNAlVBZPwYKUfar:YCkTXhZTNkPfyrQJBaVf")
                        .baseUrl("https://spark-api-open.xf-yun.com/")
                        .build())
                .defaultOptions(
                        OpenAiChatOptions.builder()
                                .model("Pro")
                                .build()
                )
                .build();
        return ChatClient
                .builder(chatModel)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .defaultAdvisors(new SimpleLoggerAdvisor())
                .build();
    }
}
