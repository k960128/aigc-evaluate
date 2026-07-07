package com.kant.llm.agent.chat.service.impl;

import com.alibaba.cloud.ai.advisor.RetrievalRerankAdvisor;
import com.kant.llm.agent.ai.model.ChatParam;
import com.kant.llm.agent.ai.model.IntentRecognitionResult;
import com.kant.llm.agent.ai.service.CommonChatService;
import com.kant.llm.agent.ai.service.IntentRecognitionService;
import com.kant.llm.agent.ai.service.PromptService;
import com.kant.llm.agent.chat.constant.ChatSource;
import com.kant.llm.agent.chat.memory.DataBaseChatMemoryRepository;
import com.kant.llm.agent.chat.service.ChatApplicationService;
import com.kant.llm.agent.chat.service.ChatConversationService;
import com.kant.llm.agent.chat.service.ChatMessageService;
import com.kant.llm.agent.ai.service.TitleSummaryService;
import com.kant.llm.rag.moduler.EvaSqlDatabaseContentRetriever;
import com.kant.llm.rag.moduler.EvalQueryRouter;
import com.kant.llm.rag.moduler.EvalQueryTransformer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;
import org.springframework.ai.rag.generation.augmentation.QueryAugmenter;
import org.springframework.ai.rag.postretrieval.document.DocumentPostProcessor;
import org.springframework.ai.rag.retrieval.join.ConcatenationDocumentJoiner;
import org.springframework.ai.rag.retrieval.join.DocumentJoiner;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

@Slf4j
@Service
public class ChatApplicationServiceImpl implements ChatApplicationService {


    private final ChatClient chatClient;
    private final VectorStore vectorStore;
    private final PromptService promptService;
    private final CommonChatService commonChatService;
    private final ChatMessageService chatMessageService;
    private final TitleSummaryService titleSummaryService;
    private final ChatConversationService chatConversationService;
    private final IntentRecognitionService intentRecognitionService;

    public ChatApplicationServiceImpl(ChatClient chatClient,
                                      VectorStore vectorStore,
                                      PromptService promptService,
                                      ChatConversationService chatConversationService,
                                      ChatMessageService chatMessageService,
                                      TitleSummaryService titleSummaryService,
                                      CommonChatService commonChatService,
                                      IntentRecognitionService intentRecognitionService) {
        this.chatClient = chatClient;
        this.vectorStore = vectorStore;
        this.promptService = promptService;
        this.chatConversationService = chatConversationService;
        this.chatMessageService = chatMessageService;
        this.titleSummaryService = titleSummaryService;
        this.commonChatService = commonChatService;
        this.intentRecognitionService = intentRecognitionService;
    }

    /**
     * 统一流式对话入口。
     * <p>
     * 封装原本散落在 {@link ChatSource} 中的完整对话流程：
     * 会话创建（可选）、异步标题生成、保存用户/助手消息、意图识别、不相关问题兜底通用对话、
     * 相关问题 RAG 对话。HTTP 接口与钉钉机器人回调均通过此方法复用同一条对话链路。
     *
     * @param userId         用户ID
     * @param content        用户问题
     * @param conversationId 会话ID（可选，为空则自动创建新会话）
     * @return 包含进度消息、[DONE] 事件及 LLM token 的 SSE 流
     */
    @Override
    public Flux<String> chat(String userId,
                             String content,
                             String conversationId,
                             ChatSource chatSource) {

        // 1. 如果conversationId为空，创建新会话
        final String finalConversationId;
        if (conversationId == null || conversationId.isBlank()) {
            String tempTitle = content.substring(0, Math.min(content.length(), 20));
            finalConversationId = chatConversationService.createConversation(userId, tempTitle);
            log.info("创建新会话: conversationId={}, tempTitle={}", finalConversationId, tempTitle);

            // 异步：用虚拟线程调用 LLM 生成摘要标题，完成后回写到数据库
            Thread.ofVirtual().name("title-summary-" + finalConversationId).start(() -> {
                try {
                    String aiTitle = titleSummaryService.generateTitle(content);
                    Boolean respFlag = chatConversationService.updateTitle(finalConversationId, aiTitle);
                    log.info("异步标题更新完成: respFlag={}, conversationId={}, title={}", respFlag, finalConversationId, aiTitle);
                } catch (Exception e) {
                    log.warn("异步标题生成失败, 保留临时标题: conversationId={}", finalConversationId, e);
                }
            });

        } else {
            finalConversationId = conversationId;
        }

        // 2. 保存用户消息
        String messageId = chatMessageService.saveUserMessage(finalConversationId, content);
        log.info("保存用户消息: messageId={}, conversationId={}, content={}", messageId, finalConversationId, content);
        String assistantMessageId = chatMessageService.saveAssistantMessage(finalConversationId);
        // 3. 组装流程内容返回给前端
        return Flux.just("[PROGRESS]:正在识别您的意图...")
                .concatWith(Mono.fromCallable(() -> intentRecognitionService.chat(finalConversationId, content))
                        .subscribeOn(Schedulers.boundedElastic())
                        .flatMapMany(intentRecognitionResult -> {

                            // 不相关问题：走通用对话
                            if (!intentRecognitionResult.related()) {
                                return Flux.concat(
                                        Flux.just("[PROGRESS]:正在为您生成回答..."),
                                        commonChatService.chatStream(userId, content)
                                );
                            }
                            // 相关问题：走 RAG 流程
                            return ragChatProcess(new ChatParam(userId, finalConversationId, messageId, content, assistantMessageId, intentRecognitionResult, chatSource));
                        }))
                .doOnError(e -> log.error("流式对话异常", e))
                .concatWith(Mono.just("[DONE]:" + finalConversationId));
    }

    /**
     * RAG 流式对话。
     * <p>
     * 1. 根据意图识别结果，判断是否需要车辆信息
     * 2. 如果车辆信息不完善，则返回车辆信息不完善提示
     * 3. 根据意图识别结果，判断是否需要车辆信息
     * </p>
     */
    public Flux<String> ragChatProcess(ChatParam chatParam) {
        return Flux.<String>create(sink -> {
                    // 进度回调：同时写入 sink 和外部回调
                    Consumer<String> processCallback = sink::next;

                    // 1. 构建查询转换器(自定义回调进度)
                    EvalQueryTransformer evalQueryTransformer = new EvalQueryTransformer(chatClient, chatParam.messageId(), processCallback);

                    // 2. 多检索器路由
                    EvalQueryRouter evalQueryRouter = new EvalQueryRouter(buildRoutingRetrievers(), chatClient, processCallback);

                    // 4. 获取业务系统提示词
                    String prompt = promptService.getPrompt(chatParam.intentRecognitionResult());

                    // 5. 聚合-去重-按 score 排序（仅用于本地验证 ConcatenationDocumentJoiner） TODO 优化更改为多路结果怎么合并
                    DocumentJoiner documentJoiner = new ConcatenationDocumentJoiner();

                    // 6. 构建后处理器：解决“合并后的结果怎么筛选、排序、压缩”。 TODO 优化调用重排序模型
                    DocumentPostProcessor filterProcessor = (query, documents) -> documents.stream()
                            .filter(document -> document.getScore() == null || document.getScore() >= 0.75)
                            .limit(5)
                            .toList();

                    // 7. 上下文的注入
                    QueryAugmenter queryAugmenter = ContextualQueryAugmenter.builder()
                            .allowEmptyContext(true) // 允许空上下文，避免空指针异常
                            .emptyContextPromptTemplate(new PromptTemplate(prompt)) // 将提示词作为上下文注入
                            .build();

                    // 6. 构建检索增强器
                    RetrievalAugmentationAdvisor augmentationAdvisor = RetrievalAugmentationAdvisor
                            .builder()
                            .documentRetriever(evalQueryRouter)
                            .queryTransformers(evalQueryTransformer) // 自定义改写起
                            .documentJoiner(documentJoiner) // 自定义聚合器（多层去重+RRF）
                            .documentPostProcessors(filterProcessor)
                            .queryAugmenter(queryAugmenter) // 自定义注入模板
                            .build();
                    // 订阅 LLM 流式输出，桥接到 sink
                    AtomicBoolean firstToken = new AtomicBoolean(true);
                    StringBuilder contentBuilder = new StringBuilder();

                    Disposable disposable = chatClient.prompt()
                            .user(chatParam.content())
                            .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, chatParam.conversationId()))
                            .advisors(augmentationAdvisor)
                            .stream()
                            .content().doOnNext(token -> {
                                // 首个 token 到达时，如果之前没有发出"正在生成回答"，则补发
                                // （正常情况下由 ProgressAwareContentAggregator 已发出，此处为兜底）
                                firstToken.compareAndSet(true, false);// 标记已开始接收 token
                                contentBuilder.append(token);
                            })
                            .doOnComplete(() -> chatMessageService.updateContent(chatParam.assistantMessageId(), contentBuilder.toString()))
                            .subscribe(sink::next, sink::error, sink::complete);

                    // 取消时同步取消内部订阅
                    sink.onCancel(disposable);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .publishOn(Schedulers.parallel());
    }


    /**
     * 构建检索路由
     *
     * @return 检索器列表
     */
    private List<DocumentRetriever> buildRoutingRetrievers() {
        // 构建数据库检索器
        EvaSqlDatabaseContentRetriever sqlRetriever = new EvaSqlDatabaseContentRetriever();
        // 构建向量检索器
        VectorStoreDocumentRetriever embeddingRetriever = VectorStoreDocumentRetriever
                .builder()
                .vectorStore(vectorStore) // 必需：绑定向量存储
                .topK(5) // 返回最相似的 5 个文档
                .similarityThreshold(0.6) // 相似度低于 0.6 的过滤掉
//                .filterExpression() 元数据过滤表达式
                .build();
        // TODO 其他检索器 图数据库或者ES检索器

        // 返回检索器列表
        return List.of(sqlRetriever, embeddingRetriever);
    }

    @Override
    public Flux<String> evalChat(String userId, String content, String conversationId, ChatSource chatSource) {
        return null;
    }
}
