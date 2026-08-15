package com.kant.llm.agent.chat.service.impl;

import com.alibaba.cloud.ai.advisor.RetrievalRerankAdvisor;
import com.kant.llm.agent.ai.model.ChatParam;
import com.kant.llm.agent.ai.model.IntentRecognitionResult;
import com.kant.llm.agent.ai.model.SecurityEvalChatParam;
import com.kant.llm.agent.ai.model.SecurityEvalPendingClarification;
import com.kant.llm.agent.ai.model.SecurityEvalSlotValidationResult;
import com.kant.llm.agent.ai.service.CommonChatService;
import com.kant.llm.agent.ai.service.IntentRecognitionService;
import com.kant.llm.agent.ai.service.PromptService;
import com.kant.llm.agent.ai.service.SecurityEvalClarificationService;
import com.kant.llm.agent.ai.service.SecurityEvalPromptService;
import com.kant.llm.agent.ai.slot.SecurityEvalSlotValidator;
import com.kant.llm.agent.chat.constant.ChatSource;
import com.kant.llm.agent.chat.memory.DataBaseChatMemoryRepository;
import com.kant.llm.agent.chat.service.ChatApplicationService;
import com.kant.llm.agent.chat.service.ChatConversationService;
import com.kant.llm.agent.chat.service.ChatMessageService;
import com.kant.llm.agent.ai.service.TitleSummaryService;
import com.kant.llm.rag.moduler.EvaSqlDatabaseContentRetriever;
import com.kant.llm.rag.moduler.EvalQueryRouter;
import com.kant.llm.rag.moduler.EvalQueryTransformer;
import com.kant.llm.rag.moduler.SecurityEvalQueryRouter;
import com.kant.llm.rag.moduler.SecurityEvalQueryTransformer;
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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

@Slf4j
@Service
public class ChatApplicationServiceImpl implements ChatApplicationService {


    private final ChatClient chatClient;
    private final VectorStore vectorStore;
    private final DataSource dataSource;
    private final ChatClient plainChatClient;
    private final PromptService promptService;
    private final CommonChatService commonChatService;
    private final ChatMessageService chatMessageService;
    private final TitleSummaryService titleSummaryService;
    private final ChatConversationService chatConversationService;
    private final IntentRecognitionService intentRecognitionService;
    private final SecurityEvalClarificationService securityEvalClarificationService;
    private final SecurityEvalPromptService securityEvalPromptService;
    /**
     * 安全评测槽位校验器，用于在意图识别后、后续能力执行前做后端确定性校验。
     */
    private final SecurityEvalSlotValidator securityEvalSlotValidator;

    public ChatApplicationServiceImpl(@Qualifier("chatClient") ChatClient chatClient,
                                      @Qualifier("plainChatClient") ChatClient plainChatClient,
                                      VectorStore vectorStore,
                                      DataSource dataSource,
                                      PromptService promptService,
                                      ChatConversationService chatConversationService,
                                      ChatMessageService chatMessageService,
                                      TitleSummaryService titleSummaryService,
                                      CommonChatService commonChatService,
                                      IntentRecognitionService intentRecognitionService,
                                      SecurityEvalClarificationService securityEvalClarificationService,
                                      SecurityEvalPromptService securityEvalPromptService,
                                      SecurityEvalSlotValidator securityEvalSlotValidator) {
        this.chatClient = chatClient;
        this.vectorStore = vectorStore;
        this.dataSource = dataSource;
        this.promptService = promptService;
        this.plainChatClient = plainChatClient;
        this.commonChatService = commonChatService;
        this.chatMessageService = chatMessageService;
        this.titleSummaryService = titleSummaryService;
        this.chatConversationService = chatConversationService;
        this.intentRecognitionService = intentRecognitionService;
        this.securityEvalClarificationService = securityEvalClarificationService;
        this.securityEvalPromptService = securityEvalPromptService;
        this.securityEvalSlotValidator = securityEvalSlotValidator;
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
                .concatWith(Mono.fromCallable(() -> intentRecognitionService.chat(content))
                        .subscribeOn(Schedulers.boundedElastic())
                        .flatMapMany(intentRecognitionResult -> {

                            // 不相关问题：走通用对话
                            if (!intentRecognitionResult.related()) {
                                StringBuilder contentBuilder = new StringBuilder();
                                return Flux.concat(
                                        Flux.just("[PROGRESS]:正在为您生成回答..."),
                                        commonChatService.chatStream(userId, content, finalConversationId)
                                                .doOnNext(contentBuilder::append)
                                                .doOnComplete(() -> chatMessageService.updateContent(assistantMessageId, contentBuilder.toString()))
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
                    EvalQueryTransformer evalQueryTransformer = new EvalQueryTransformer(plainChatClient,
                            chatParam.messageId(),
                            processCallback);

                    // 2. 多检索器路由
                    EvalQueryRouter evalQueryRouter = new EvalQueryRouter(buildRoutingRetrievers(),
                            plainChatClient,
                            processCallback);

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
        EvaSqlDatabaseContentRetriever sqlRetriever = new EvaSqlDatabaseContentRetriever(plainChatClient,
                dataSource);
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

    /**
     * 安全评测助手流式对话入口。
     *
     * <p>当前阶段只完成安全评测专用意图识别和 Slot Validator 接入：
     * 先使用 {@link IntentRecognitionService#securityEvalChat(String)} 得到结构化意图，
     * 再使用 {@link SecurityEvalSlotValidator} 做后端确定性槽位校验。</p>
     *
     * <p>后续接入 Skill Router 时，可以将 {@code validationResult.executable() == true}
     * 的分支替换为具体安全评测 Skill 分发逻辑。</p>
     *
     * @param userId         当前登录用户 ID
     * @param content        用户输入内容
     * @param conversationId 会话 ID；为空时自动创建新会话
     * @param chatSource     对话来源
     * @return SSE 流式响应
     */
    @Override
    public Flux<String> evalChat(String userId,
                                 String content,
                                 String conversationId,
                                 ChatSource chatSource) {
        final String finalConversationId;
        if (conversationId == null || conversationId.isBlank()) {
            // 没有传入会话 ID 时，沿用普通聊天的会话创建方式，先用用户问题前 20 个字符作为临时标题。
            String tempTitle = content.substring(0, Math.min(content.length(), 20));
            finalConversationId = chatConversationService.createConversation(userId, tempTitle);
            log.info("创建安全评测会话: conversationId={}, tempTitle={}", finalConversationId, tempTitle);

            // 标题生成不阻塞主对话链路，失败时保留临时标题即可。
            Thread.ofVirtual().name("title-summary-" + finalConversationId).start(() -> {
                try {
                    String aiTitle = titleSummaryService.generateTitle(content);
                    Boolean respFlag = chatConversationService.updateTitle(finalConversationId, aiTitle);
                    log.info("安全评测会话标题更新完成: respFlag={}, conversationId={}, title={}", respFlag, finalConversationId, aiTitle);
                } catch (Exception e) {
                    log.warn("安全评测会话标题生成失败, 保留临时标题: conversationId={}", finalConversationId, e);
                }
            });
        } else {
            finalConversationId = conversationId;
        }

        // 先保存用户消息和空的助手消息，后续将识别/校验结果回写到助手消息中。
        String messageId = chatMessageService.saveUserMessage(finalConversationId, content);
        log.info("保存安全评测用户消息: messageId={}, conversationId={}, content={}", messageId, finalConversationId, content);
        String assistantMessageId = chatMessageService.saveAssistantMessage(finalConversationId);

        // 如果当前会话存在待补槽状态，本轮只做槽位补充，不再重新识别 intent。
        SecurityEvalPendingClarification pendingClarification =
                securityEvalClarificationService.findPending(finalConversationId).orElse(null);
        if (pendingClarification != null) {
            return securityEvalPendingClarificationProcess(userId,
                    finalConversationId,
                    messageId,
                    content,
                    assistantMessageId,
                    chatSource,
                    pendingClarification);
        }

        return Flux.just("[PROGRESS]:正在识别安全评测意图...")
                // 意图识别是阻塞式 LLM 调用，放到 boundedElastic，避免占用响应式主线程。
                .concatWith(Mono.fromCallable(() -> intentRecognitionService.securityEvalChat(content))
                        .subscribeOn(Schedulers.boundedElastic())
                        .flatMapMany(intentRecognitionResult -> {
                            // 后端重新校验关键槽位，不直接相信模型返回的 needClarification/missingSlots。
                            SecurityEvalSlotValidationResult validationResult =
                                    securityEvalSlotValidator.validate(intentRecognitionResult);

                            // 缺少关键槽位时，直接返回稳定的后端澄清问题。
                            if (validationResult.needClarification()) {
                                securityEvalClarificationService.savePending(finalConversationId,
                                        content,
                                        intentRecognitionResult,
                                        validationResult);
                                return saveAndEmit(assistantMessageId, validationResult.clarificationQuestion());
                            }

                            // 越界、无关或未知意图不进入后续执行链路。
                            if (!validationResult.executable()) {
                                return saveAndEmit(assistantMessageId, buildSecurityEvalNotExecutableReply(validationResult));
                            }

                            // 当前还未接 Skill Router，先返回已识别和已校验的结构化结果，方便前后端联调。
                            return securityEvalRagChatProcess(new SecurityEvalChatParam(userId,
                                    finalConversationId,
                                    messageId,
                                    content,
                                    assistantMessageId,
                                    intentRecognitionResult,
                                    validationResult,
                                    chatSource));
                        }))
                .doOnError(e -> log.error("安全评测流式对话异常", e))
                .concatWith(Mono.just("[DONE]:" + finalConversationId));
    }

    /**
     * 安全评测多轮补槽处理流程。
     *
     * <p>该方法只在当前 {@code conversationId} 存在待补槽状态时执行。
     * 与完整意图识别不同，这里会沿用上一轮已经确认的 intent，只从本轮用户输入中抽取缺失槽位，
     * 再交给 {@link SecurityEvalSlotValidator} 做一次后端确定性校验。</p>
     *
     * <p>处理结果分三类：</p>
     * <ul>
     *     <li>仍然缺槽：更新 pending 状态，并继续返回后端生成的澄清问题。</li>
     *     <li>补槽通过：清理 pending 状态，并使用累计上下文进入安全评测 RAG 流程。</li>
     *     <li>状态异常不可执行：清理 pending 状态，并返回安全评测助手边界提示。</li>
     * </ul>
     *
     * @param userId                 当前登录用户 ID
     * @param finalConversationId    当前会话 ID
     * @param messageId              当前用户消息 ID
     * @param content                用户本轮补充内容
     * @param assistantMessageId     当前助手消息 ID
     * @param chatSource             对话来源
     * @param pendingClarification   当前会话待补槽状态
     * @return SSE 流式响应
     */
    private Flux<String> securityEvalPendingClarificationProcess(String userId,
                                                                 String finalConversationId,
                                                                 String messageId,
                                                                 String content,
                                                                 String assistantMessageId,
                                                                 ChatSource chatSource,
                                                                 SecurityEvalPendingClarification pendingClarification) {
        return Flux.just("[PROGRESS]:正在补充安全评测槽位...")
                .concatWith(Mono.fromCallable(() -> securityEvalClarificationService.fillSlots(pendingClarification, content))
                        .subscribeOn(Schedulers.boundedElastic())
                        .flatMapMany(intentRecognitionResult -> {
                            // 将本轮补充追加到累计上下文，后续进入 RAG 时能同时看到原问题和补充信息。
                            String mergedContent = securityEvalClarificationService.appendUserContext(
                                    pendingClarification.userContext(),
                                    content);

                            // 补槽后仍然由后端 Slot Validator 重新计算是否满足执行条件。
                            SecurityEvalSlotValidationResult validationResult =
                                    securityEvalSlotValidator.validate(intentRecognitionResult);

                            // 仍缺关键槽位时，更新 pending，下一轮继续沿用同一个 intent 补槽。
                            if (validationResult.needClarification()) {
                                securityEvalClarificationService.savePending(finalConversationId,
                                        mergedContent,
                                        intentRecognitionResult,
                                        validationResult);
                                return saveAndEmit(assistantMessageId, validationResult.clarificationQuestion());
                            }

                            // 如果 pending 状态异常导致不可执行，清理状态后返回边界提示。
                            if (!validationResult.executable()) {
                                securityEvalClarificationService.clearPending(finalConversationId);
                                return saveAndEmit(assistantMessageId, buildSecurityEvalNotExecutableReply(validationResult));
                            }

                            // 补槽通过后必须清理 pending，避免下一轮正常问题被误当作补充信息。
                            securityEvalClarificationService.clearPending(finalConversationId);
                            return securityEvalRagChatProcess(new SecurityEvalChatParam(userId,
                                    finalConversationId,
                                    messageId,
                                    mergedContent,
                                    assistantMessageId,
                                    intentRecognitionResult,
                                    validationResult,
                                    chatSource));
                        }))
                .doOnError(e -> log.error("安全评测多轮补槽异常", e))
                .concatWith(Mono.just("[DONE]:" + finalConversationId));
    }

    /**
     * 安全评测 RAG 流式处理。
     *
     * <p>该流程发生在安全评测意图识别和 Slot Validator 之后：
     * 先使用 {@link SecurityEvalQueryTransformer} 基于 intent 与 slots 改写检索问题，
     * 再将改写后的问题交给检索增强生成链路。</p>
     *
     * <p>当前阶段还没有引入独立的安全评测 Skill Router，因此这里先作为安全评测 RAG 的统一入口。
     * 后续可以按 intent 拆分为链路诊断、资产查询、判例检索、报告辅助等独立 Skill。</p>
     *
     * @param chatParam 安全评测 RAG 对话参数
     * @return SSE 流式响应
     */
    public Flux<String> securityEvalRagChatProcess(SecurityEvalChatParam chatParam) {
        return Flux.<String>create(sink -> {
                    Consumer<String> processCallback = sink::next;

                    //1. 改写检索问题
                    SecurityEvalQueryTransformer queryTransformer = new SecurityEvalQueryTransformer(
                            plainChatClient,
                            chatMessageService,
                            chatParam.slotValidationResult().intent(),
                            chatParam.intentRecognitionResult().entities(),
                            chatParam.messageId(),
                            processCallback);
                    //2. 问题路由检索
                    SecurityEvalQueryRouter queryRouter = new SecurityEvalQueryRouter(
                            buildRoutingRetrievers(),
                            plainChatClient,
                            chatParam.slotValidationResult().intent(),
                            chatParam.intentRecognitionResult().entities(),
                            processCallback);

                    DocumentJoiner documentJoiner = new ConcatenationDocumentJoiner();

                    DocumentPostProcessor filterProcessor = (query, documents) -> documents.stream()
                            .filter(document -> document.getScore() == null || document.getScore() >= 0.75)
                            .limit(8)
                            .toList();

                    QueryAugmenter queryAugmenter = ContextualQueryAugmenter.builder()
                            .allowEmptyContext(true)
                            .emptyContextPromptTemplate(new PromptTemplate(securityEvalPromptService.getRagAnswerPrompt(chatParam)))
                            .build();

                    RetrievalAugmentationAdvisor augmentationAdvisor = RetrievalAugmentationAdvisor
                            .builder()
                            .documentRetriever(queryRouter)
                            .queryTransformers(queryTransformer)
                            .documentJoiner(documentJoiner)
                            .documentPostProcessors(filterProcessor)
                            .queryAugmenter(queryAugmenter)
                            .build();

                    StringBuilder contentBuilder = new StringBuilder();

                    Disposable disposable = chatClient.prompt()
                            .user(chatParam.content())
                            .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, chatParam.conversationId()))
                            .advisors(augmentationAdvisor)
                            .stream()
                            .content()
                            .doOnNext(contentBuilder::append)
                            .doOnComplete(() -> chatMessageService.updateContent(chatParam.assistantMessageId(), contentBuilder.toString()))
                            .subscribe(sink::next, sink::error, sink::complete);

                    sink.onCancel(disposable);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .publishOn(Schedulers.parallel());
    }

    /**
     * 保存助手消息内容并作为单段 SSE 内容返回。
     *
     * <p>该方法用于澄清追问、越界提示等不需要进入流式 RAG 的场景。
     * 内容会先回写助手消息，再作为 SSE 单段内容返回给前端。</p>
     *
     * @param assistantMessageId 预创建的助手消息 ID
     * @param content            要保存并返回的助手内容
     * @return 单段响应流
     */
    private Flux<String> saveAndEmit(String assistantMessageId, String content) {
        chatMessageService.updateContent(assistantMessageId, content);
        return Flux.just(content);
    }

    /**
     * 构造安全评测不可执行时的兜底回复。
     *
     * <p>不可执行通常意味着请求不属于安全评测助手范围、意图为 OUT_OF_SCOPE，
     * 或模型输出了后端无法识别的 UNKNOWN 意图。</p>
     *
     * @param validationResult 槽位校验结果
     * @return 面向用户的边界提示
     */
    private String buildSecurityEvalNotExecutableReply(SecurityEvalSlotValidationResult validationResult) {
        return "当前请求不属于安全评测助手可处理范围，或意图识别结果无法进入安全评测执行链路。"
                + "请围绕评测链路诊断、规则特征溯源、误杀漏判分析、合规问答、历史案例检索、"
                + "资产查询、特征提取或报告辅助重新描述。"
                + "\n\nintent: " + validationResult.intent();
    }
}
