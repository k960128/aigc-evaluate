package com.kant.llm.agent.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kant.llm.agent.ai.model.SecurityEvalIntentRecognitionResult;
import com.kant.llm.agent.ai.model.SecurityEvalPendingClarification;
import com.kant.llm.agent.ai.model.SecurityEvalSlotFillResult;
import com.kant.llm.agent.ai.model.SecurityEvalSlotValidationResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 安全评测多轮补槽服务。
 *
 * <p>该服务解决的是“第一轮意图明确但缺少关键槽位，第二轮用户补充信息却被重新识别成其他意图”的问题。
 * 只要同一个 {@code conversationId} 下存在待补槽状态，应用层就会优先调用本服务做槽位补充，
 * 而不是再次执行完整意图识别。</p>
 *
 * <p>当前实现使用进程内 {@link ConcurrentHashMap} 保存 pending 状态，适合先验证业务闭环。
 * 后续如果服务需要多实例部署、重启恢复或跨节点会话连续性，可以把存储替换为 Redis 或数据库。</p>
 */
@Slf4j
@Service
public class SecurityEvalClarificationService {

    /**
     * 待补槽状态过期时间。
     *
     * <p>用户长时间不补充时，继续沿用旧意图容易造成误路由，因此这里设置一个温和的过期时间。
     * 过期后同一会话的新输入会重新进入完整意图识别链路。</p>
     */
    private static final Duration PENDING_EXPIRE_AFTER = Duration.ofMinutes(30);

    /**
     * 安全评测补槽专用 Prompt。
     *
     * <p>该 Prompt 明确要求模型不要重判 intent，只能从用户补充内容中抽取上一轮缺失槽位。</p>
     */
    @Value("classpath:/prompts/security-eval-slot-fill-prompt.txt")
    private Resource slotFillPrompt;

    /**
     * 会话级待补槽状态表。
     *
     * <p>key 为 conversationId，value 为该会话最近一次未补齐的安全评测意图上下文。</p>
     */
    private final Map<String, SecurityEvalPendingClarification> pendingClarifications = new ConcurrentHashMap<>();

    private final ChatClient plainChatClient;
    private final ObjectMapper objectMapper;

    public SecurityEvalClarificationService(@Qualifier("plainChatClient") ChatClient plainChatClient,
                                            ObjectMapper objectMapper) {
        this.plainChatClient = plainChatClient;
        this.objectMapper = objectMapper;
    }

    /**
     * 查询指定会话是否存在待补槽状态。
     *
     * <p>如果 pending 状态已过期，本方法会顺手清理并返回空，调用方即可回到正常意图识别链路。</p>
     *
     * @param conversationId 会话 ID
     * @return 未过期的待补槽状态；不存在或已过期时为空
     */
    public Optional<SecurityEvalPendingClarification> findPending(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return Optional.empty();
        }
        SecurityEvalPendingClarification pending = pendingClarifications.get(conversationId);
        if (pending == null) {
            return Optional.empty();
        }
        if (isExpired(pending)) {
            pendingClarifications.remove(conversationId);
            log.info("安全评测待补槽状态已过期并清理: conversationId={}", conversationId);
            return Optional.empty();
        }
        return Optional.of(pending);
    }

    /**
     * 保存或更新会话待补槽状态。
     *
     * <p>第一次缺槽时创建 pending；后续用户补充后如果仍缺槽，则用合并后的 entities 和累计上下文更新 pending，
     * 这样第三轮补充可以继续继承前两轮信息。</p>
     *
     * @param conversationId          会话 ID
     * @param userContext             累计用户上下文
     * @param intentRecognitionResult 当前已确认意图及已抽取槽位
     * @param validationResult        当前后端槽位校验结果
     */
    public void savePending(String conversationId,
                            String userContext,
                            SecurityEvalIntentRecognitionResult intentRecognitionResult,
                            SecurityEvalSlotValidationResult validationResult) {
        if (conversationId == null || conversationId.isBlank()) {
            return;
        }
        cleanupExpiredPending();

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime createdAt = Optional.ofNullable(pendingClarifications.get(conversationId))
                .map(SecurityEvalPendingClarification::createdAt)
                .orElse(now);

        pendingClarifications.put(conversationId, new SecurityEvalPendingClarification(
                conversationId,
                userContext,
                intentRecognitionResult,
                validationResult,
                createdAt,
                now
        ));
        log.info("保存安全评测待补槽状态: conversationId={}, intent={}, missingSlots={}",
                conversationId, validationResult.intent(), validationResult.missingSlots());
    }

    /**
     * 清理指定会话的待补槽状态。
     *
     * <p>当补槽通过、请求进入 RAG/Skill 执行链路，或链路判断当前状态不可执行时，应调用本方法避免旧状态污染后续对话。</p>
     *
     * @param conversationId 会话 ID
     */
    public void clearPending(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return;
        }
        pendingClarifications.remove(conversationId);
        log.info("清理安全评测待补槽状态: conversationId={}", conversationId);
    }

    /**
     * 基于待补槽状态和用户最新补充内容合并出新的意图识别结果。
     *
     * <p>该方法不会修改 intent，只会调用补槽 Prompt 抽取新增 entities，
     * 再把新增 entities 合并到上一轮已经确认的 {@link SecurityEvalIntentRecognitionResult} 中。
     * 合并后的结果仍需要调用 {@code SecurityEvalSlotValidator} 重新做后端确定性校验。</p>
     *
     * @param pending           当前会话待补槽状态
     * @param supplementContent 用户最新补充内容
     * @return 合并补槽信息后的安全评测意图识别结果
     */
    public SecurityEvalIntentRecognitionResult fillSlots(SecurityEvalPendingClarification pending,
                                                         String supplementContent) {
        SecurityEvalSlotFillResult fillResult = requestSlotFill(pending, supplementContent);
        SecurityEvalIntentRecognitionResult previous = pending.intentRecognitionResult();
        SecurityEvalIntentRecognitionResult.Entities mergedEntities =
                mergeEntities(previous.entities(), fillResult == null ? null : fillResult.entities());

        return new SecurityEvalIntentRecognitionResult(
                mergeReasoning(previous.reasoning(), fillResult),
                previous.related(),
                previous.intent(),
                previous.confidence(),
                previous.requireHumanReview(),
                false,
                List.of(),
                null,
                mergedEntities
        );
    }

    /**
     * 追加用户补充内容，形成后续 RAG/Skill 可理解的完整问题。
     *
     * <p>例如第一轮是“帮我分析这个结果为什么漏判”，第二轮是“result_detail_id 是 123”，
     * 那么最终进入 RAG 的内容应同时包含两轮信息，而不是只拿第二轮的 ID。</p>
     *
     * @param currentContext    当前累计上下文
     * @param supplementContent 用户最新补充内容
     * @return 追加后的累计上下文
     */
    public String appendUserContext(String currentContext, String supplementContent) {
        if (supplementContent == null || supplementContent.isBlank()) {
            return nullToEmpty(currentContext);
        }
        if (currentContext == null || currentContext.isBlank()) {
            return supplementContent;
        }
        return currentContext + "\n\n用户补充：" + supplementContent;
    }

    /**
     * 调用补槽专用 Prompt 抽取用户补充内容中的槽位。
     *
     * <p>Prompt 输入会带上上一轮 intent、已有 entities 和缺失槽位列表，帮助模型把“123”这类短文本
     * 归因到正确 ID 字段。</p>
     *
     * @param pending           当前待补槽状态
     * @param supplementContent 用户最新补充内容
     * @return 结构化补槽结果
     */
    private SecurityEvalSlotFillResult requestSlotFill(SecurityEvalPendingClarification pending,
                                                       String supplementContent) {
        return plainChatClient
                .prompt()
                .system(slotFillPrompt)
                .user(u -> u.text(buildSlotFillUserPrompt(pending, supplementContent)))
                .call()
                .entity(SecurityEvalSlotFillResult.class);
    }

    /**
     * 构造补槽模型的用户消息。
     *
     * <p>这里把“上一轮已经确认的信息”和“本轮用户补充”显式拆开，
     * 减少模型把补充内容当成新问题重新理解的概率。</p>
     *
     * @param pending           当前待补槽状态
     * @param supplementContent 用户最新补充内容
     * @return 发送给补槽模型的用户消息
     */
    private String buildSlotFillUserPrompt(SecurityEvalPendingClarification pending, String supplementContent) {
        return """
                上一轮已确认 intent：
                %s

                上一轮澄清问题：
                %s

                上一轮缺失槽位 missingSlots：
                %s

                已有 entities：
                %s

                用户累计上下文：
                %s

                用户最新补充内容：
                %s
                """.formatted(
                pending.slotValidationResult().intent(),
                nullToEmpty(pending.slotValidationResult().clarificationQuestion()),
                pending.slotValidationResult().missingSlots(),
                toJson(pending.intentRecognitionResult().entities()),
                nullToEmpty(pending.userContext()),
                nullToEmpty(supplementContent)
        );
    }

    /**
     * 合并上一轮 entities 和本轮补槽 entities。
     *
     * <p>合并规则为：本轮补充中有有效值时优先采用本轮值，否则保留上一轮值。
     * 这样既支持用户补充缺失槽位，也支持用户纠正上一轮已抽取但不准确的槽位。</p>
     *
     * @param previous 上一轮已经抽取的实体槽位
     * @param current  本轮补槽抽取出的实体槽位
     * @return 合并后的实体槽位
     */
    private SecurityEvalIntentRecognitionResult.Entities mergeEntities(
            SecurityEvalIntentRecognitionResult.Entities previous,
            SecurityEvalIntentRecognitionResult.Entities current) {
        if (previous == null) {
            return current;
        }
        if (current == null) {
            return previous;
        }

        return new SecurityEvalIntentRecognitionResult.Entities(
                chooseText(current.input_text(), previous.input_text()),
                chooseText(current.model_output(), previous.model_output()),
                chooseValue(current.risk_details_id(), previous.risk_details_id()),
                chooseText(current.risk_details_name(), previous.risk_details_name()),
                chooseValue(current.category_id(), previous.category_id()),
                chooseText(current.category_name(), previous.category_name()),
                chooseText(current.scenario(), previous.scenario()),
                chooseText(current.model_name(), previous.model_name()),
                chooseValue(current.task_id(), previous.task_id()),
                chooseValue(current.task_detail_id(), previous.task_detail_id()),
                chooseValue(current.result_detail_id(), previous.result_detail_id()),
                chooseValue(current.dataset_id(), previous.dataset_id()),
                chooseValue(current.sample_id(), previous.sample_id()),
                chooseValue(current.rule_id(), previous.rule_id()),
                chooseValue(current.feature_id(), previous.feature_id()),
                chooseValue(current.keyword_id(), previous.keyword_id()),
                chooseText(current.time_range(), previous.time_range()),
                chooseValue(current.top_k(), previous.top_k()),
                chooseValue(current.l2_threshold(), previous.l2_threshold()),
                chooseValue(current.l2_top_k(), previous.l2_top_k()),
                chooseList(current.risk_scope_ids(), previous.risk_scope_ids()),
                chooseText(current.judge_version(), previous.judge_version()),
                chooseText(current.output_format(), previous.output_format()),
                chooseList(current.candidate_features(), previous.candidate_features()),
                chooseList(current.keywords(), previous.keywords())
        );
    }

    /**
     * 合并意图识别理由和补槽理由，便于后续审计排查。
     *
     * @param previousReasoning 上一轮意图识别理由
     * @param fillResult        本轮补槽结果
     * @return 合并后的理由文本
     */
    private String mergeReasoning(String previousReasoning, SecurityEvalSlotFillResult fillResult) {
        if (fillResult == null || fillResult.reasoning() == null || fillResult.reasoning().isBlank()) {
            return previousReasoning;
        }
        if (previousReasoning == null || previousReasoning.isBlank()) {
            return "补槽：" + fillResult.reasoning();
        }
        return previousReasoning + "\n补槽：" + fillResult.reasoning();
    }

    /**
     * 当前 pending 状态是否已过期。
     *
     * @param pending 待补槽状态
     * @return true 表示已经超过允许保留时间
     */
    private boolean isExpired(SecurityEvalPendingClarification pending) {
        return pending.updatedAt() != null
                && Duration.between(pending.updatedAt(), LocalDateTime.now()).compareTo(PENDING_EXPIRE_AFTER) > 0;
    }

    /**
     * 清理所有已过期的 pending 状态。
     *
     * <p>该方法在保存新 pending 时顺手执行，避免长时间运行后内存中堆积已失效会话状态。</p>
     */
    private void cleanupExpiredPending() {
        pendingClarifications.entrySet().removeIf(entry -> isExpired(entry.getValue()));
    }

    /**
     * 文本值选择器。
     *
     * @param current  本轮补槽值
     * @param previous 上一轮旧值
     * @return 本轮存在有效文本时返回本轮值，否则返回旧值
     */
    private String chooseText(String current, String previous) {
        return current == null || current.isBlank() ? previous : current;
    }

    /**
     * 对象值选择器。
     *
     * @param current  本轮补槽值
     * @param previous 上一轮旧值
     * @return 本轮非 null 时返回本轮值，否则返回旧值
     */
    private <T> T chooseValue(T current, T previous) {
        return current == null ? previous : current;
    }

    /**
     * 列表值选择器。
     *
     * @param current  本轮补槽列表
     * @param previous 上一轮旧列表
     * @return 本轮列表非空时返回本轮列表，否则返回旧列表
     */
    private <T> List<T> chooseList(List<T> current, List<T> previous) {
        return current == null || current.isEmpty() ? previous : current;
    }

    /**
     * 将对象序列化为 JSON，方便模型稳定读取上下文。
     *
     * @param value 待序列化对象
     * @return JSON 字符串；序列化失败时退化为对象的 {@code toString()}
     */
    private String toJson(Object value) {
        if (value == null) {
            return "";
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return String.valueOf(value);
        }
    }

    /**
     * null 文本兜底为空字符串。
     *
     * @param value 原始文本
     * @return 非 null 文本
     */
    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
