package com.kant.llm.agent.chat.service.impl;

import com.alibaba.cloud.ai.advisor.RetrievalRerankAdvisor;
import com.kant.llm.agent.ai.model.ChatParam;
import com.kant.llm.agent.ai.model.IntentRecognitionResult;
import com.kant.llm.agent.ai.service.CommonChatService;
import com.kant.llm.agent.ai.service.IntentRecognitionService;
import com.kant.llm.agent.ai.service.PromptService;
import com.kant.llm.agent.chat.constant.ChatSource;
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
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

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

    private final ChatMemory examplesChatMemory;

    public ChatApplicationServiceImpl(ChatClient chatClient,
                                      VectorStore vectorStore,
                                      PromptService promptService,
                                      ChatConversationService chatConversationService,
                                      ChatMessageService chatMessageService,
                                      TitleSummaryService titleSummaryService,
                                      CommonChatService commonChatService,
                                      IntentRecognitionService intentRecognitionService,
                                      ChatMemory examplesChatMemory) {
        this.chatClient = chatClient;
        this.vectorStore = vectorStore;
        this.promptService = promptService;
        this.chatConversationService = chatConversationService;
        this.chatMessageService = chatMessageService;
        this.titleSummaryService = titleSummaryService;
        this.commonChatService = commonChatService;
        this.intentRecognitionService = intentRecognitionService;
        this.examplesChatMemory = examplesChatMemory;
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
        // 3. 意图识别
        IntentRecognitionResult intentRecognitionResult = intentRecognitionService.chat(content);
        // 4. 如果用户问题不相关，调用一个通用LLM做对话
        if (!intentRecognitionResult.related()) {
            return commonChatService.chatStream(content)
                    .concatWith(Flux.just("[DONE]:" + finalConversationId));
        } else {
            return ragChat(new ChatParam(userId, finalConversationId, messageId, content, assistantMessageId, intentRecognitionResult, chatSource));
        }

//        // 3. 流式返回：先发送意图识别进度，再执行意图识别
//        return Flux.just("[PROGRESS]:正在识别您的意图...")
//                .concatWith(Flux.concat(
//                        Flux.just("[PROGRESS]:正在生成回答..."),
//                        Flux.just("balibajiasdofjoewfgenwgojoiejriojgog")
//                ))
//                .subscribeOn(Schedulers.boundedElastic())
//                .doOnError(e ->  log.error("流式对话异常: conversationId={}", finalConversationId, e))
//                // 6. 在流末尾追加一条 [DONE] 事件，携带 conversationId
//                .concatWith(Mono.just("[DONE]:" + finalConversationId));
    }


    /**
     * RAG 流式对话。
     * <p>
     * 1. 根据意图识别结果，判断是否需要车辆信息
     * 2. 如果车辆信息不完善，则返回车辆信息不完善提示
     * 3. 根据意图识别结果，判断是否需要车辆信息
     * </p>
     */
    public Flux<String> ragChat(ChatParam chatParam) {
        // 1. 构建查询转换器
        EvalQueryTransformer evalQueryTransformer = new EvalQueryTransformer(chatClient, chatParam.messageId());

        // 2. 多检索器路由
        EvalQueryRouter evalQueryRouter = new EvalQueryRouter(buildRoutingRetrievers(), chatClient);

        // 4. 获取业务系统提示词
        String prompt = promptService.getPrompt(chatParam.intentRecognitionResult());

        // 5. 聚合-去重-按 score 排序（仅用于本地验证 ConcatenationDocumentJoiner）
        DocumentJoiner documentJoiner = new ConcatenationDocumentJoiner();
        Query brakeNoiseQuery = new Query("车辆低速刹车时出现尖锐异响");
        Query engineShakeQuery = new Query("发动机怠速抖动并伴随故障灯点亮");
        Map<Query, List<List<Document>>> documentsForQuery = Map.of(
                brakeNoiseQuery, List.of(
                        List.of(
                                Document.builder()
                                        .id("repair-brake-pad-noise")
                                        .text("低速刹车出现尖锐异响时，优先检查刹车片磨损极限片、刹车盘表面拉槽以及刹车片背板是否缺少消音膏。若刹车片厚度低于 3mm，应成套更换并清洁卡钳导向销。")
                                        .metadata("source", "knowledge_base")
                                        .metadata("category", "brake_system")
                                        .score(0.92)
                                        .build(),
                                Document.builder()
                                        .id("repair-caliper-guide-pin")
                                        .text("制动卡钳导向销缺油或卡滞会导致刹车片偏磨、拖滞和异响。维修时应拆检导向销，清除旧油泥后涂抹耐高温制动润滑脂。")
                                        .metadata("source", "knowledge_base")
                                        .metadata("category", "brake_system")
                                        .score(0.81)
                                        .build()),
                        List.of(
                                Document.builder()
                                        .id("repair-brake-pad-noise")
                                        .text("刹车异响常见原因包括刹车片磨损报警片接触刹车盘、刹车盘锈蚀、卡钳回位不良。重复 ID 用于验证 joiner 去重时保留第一次出现的文档。")
                                        .metadata("source", "relational_db")
                                        .metadata("category", "brake_system")
                                        .score(0.88)
                                        .build(),
                                Document.builder()
                                        .id("repair-brake-disc-runout")
                                        .text("车辆高速制动时方向盘抖动，多与刹车盘端面跳动量过大或盘面厚薄不均有关。应使用百分表检测端面跳动，并结合盘厚数据判断是否光盘或更换。")
                                        .metadata("source", "relational_db")
                                        .metadata("category", "brake_system")
                                        .score(0.76)
                                        .build())),
                engineShakeQuery, List.of(
                        List.of(
                                Document.builder()
                                        .id("repair-spark-plug-misfire")
                                        .text("发动机怠速抖动且故障灯点亮时，应读取故障码。若出现 P0300 或单缸失火码，重点检查火花塞间隙、点火线圈、高压包插头和喷油嘴雾化状态。")
                                        .metadata("source", "knowledge_base")
                                        .metadata("category", "engine")
                                        .score(0.95)
                                        .build(),
                                Document.builder()
                                        .id("repair-throttle-body-cleaning")
                                        .text("节气门积碳会造成怠速不稳、冷启动转速波动和加速响应迟滞。清洗节气门后，部分车型需要执行怠速学习或节气门匹配。")
                                        .metadata("source", "knowledge_base")
                                        .metadata("category", "engine")
                                        .score(0.71)
                                        .build())));
        List<Document> joinedDocuments = documentJoiner.join(documentsForQuery);
        log.info("ConcatenationDocumentJoiner test documents: {}", joinedDocuments);

        DocumentPostProcessor filterProcessor = (query, documents) -> documents.stream()
                .filter(document -> document.getScore() == null || document.getScore() >= 0.75)
                .limit(5)
                .toList();


        // 5. 上下文的注入
        QueryAugmenter queryAugmenter = ContextualQueryAugmenter.builder()
                .allowEmptyContext(true)
                .emptyContextPromptTemplate(new PromptTemplate("请回答以下用户问题"))
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

        // 7. 构建流式对话
        return chatClient.prompt()
                .system(prompt)
                .user(chatParam.content())
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, chatParam.conversationId()))
                .advisors(augmentationAdvisor)
                .stream()
                .content();
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
}
