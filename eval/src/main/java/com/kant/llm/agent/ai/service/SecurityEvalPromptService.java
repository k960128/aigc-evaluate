package com.kant.llm.agent.ai.service;

import com.kant.llm.agent.ai.constant.SecurityEvalIntent;
import com.kant.llm.agent.ai.model.SecurityEvalChatParam;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;
import org.springframework.util.FileCopyUtils;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 安全评测助手 Prompt 服务。
 *
 * <p>该服务只服务安全评测助手链路，不复用旧的 {@link PromptService}，
 * 避免安全评测意图与历史汽车客服意图映射混在一起。</p>
 *
 * <p>回答阶段采用“公共业务边界 Prompt + 意图专属 Prompt”的组合方式：
 * 公共 Prompt 负责统一角色、边界、安全约束和运行时变量；
 * 意图 Prompt 负责不同安全评测能力的回答结构和侧重点。</p>
 */
@Service
public class SecurityEvalPromptService {

    private static final String COMMON_RAG_PROMPT_FILE = "security-eval-rag-common-prompt.txt";

    private static final Map<SecurityEvalIntent, String> INTENT_PROMPT_FILES = Map.of(
            SecurityEvalIntent.FULL_LINK_TRACE, "security-eval-full-link-trace-prompt.txt",
            SecurityEvalIntent.RULE_FEATURE_TRACE, "security-eval-rule-feature-trace-prompt.txt",
            SecurityEvalIntent.MISJUDGEMENT_ANALYSIS, "security-eval-misjudgement-analysis-prompt.txt",
            SecurityEvalIntent.COMPLIANCE_QA, "security-eval-compliance-qa-prompt.txt",
            SecurityEvalIntent.SIMILAR_CASE_RETRIEVAL, "security-eval-similar-case-retrieval-prompt.txt",
            SecurityEvalIntent.ASSET_DATA_QUERY, "security-eval-asset-data-query-prompt.txt",
            SecurityEvalIntent.FEATURE_EXTRACTION, "security-eval-feature-extraction-prompt.txt",
            SecurityEvalIntent.REPORT_ASSISTANCE, "security-eval-report-assistance-prompt.txt",
            SecurityEvalIntent.GENERAL_CHAT, "security-eval-general-chat-prompt.txt"
    );

    private final Map<String, String> promptCache = new ConcurrentHashMap<>();
    private final PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

    /**
     * 获取安全评测 RAG 回答阶段的 Prompt。
     *
     * <p>返回结果已经完成公共变量渲染，可直接传给 {@code PromptTemplate} 使用。
     * 如果当前意图没有专属 Prompt，则只使用公共 Prompt 作为兜底。</p>
     *
     * @param chatParam 安全评测 RAG 对话参数
     * @return 已渲染的安全评测回答 Prompt
     */
    public String getRagAnswerPrompt(SecurityEvalChatParam chatParam) {
        SecurityEvalIntent intent = chatParam.slotValidationResult().intent();
        String commonPrompt = getPromptTemplate(COMMON_RAG_PROMPT_FILE);
        String intentPrompt = getIntentPrompt(intent);

        return render(commonPrompt + "\n\n" + intentPrompt, Map.of(
                "intent", String.valueOf(intent),
                "requireHumanReview", String.valueOf(chatParam.intentRecognitionResult().requireHumanReview()),
                "reasoning", nullToEmpty(chatParam.intentRecognitionResult().reasoning())
        ));
    }

    private String getIntentPrompt(SecurityEvalIntent intent) {
        String fileName = INTENT_PROMPT_FILES.get(intent);
        if (fileName == null) {
            return "";
        }
        return getPromptTemplate(fileName);
    }

    private String getPromptTemplate(String fileName) {
        return promptCache.computeIfAbsent(fileName, this::loadPromptFromFile);
    }

    private String loadPromptFromFile(String fileName) {
        try {
            Resource resource = resolver.getResource("classpath:/prompts/" + fileName);
            return FileCopyUtils.copyToString(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("安全评测 Prompt 文件加载失败: " + fileName, e);
        }
    }

    private String render(String template, Map<String, String> variables) {
        String rendered = template;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            rendered = rendered.replace("${" + entry.getKey() + "}", nullToEmpty(entry.getValue()));
        }
        return rendered;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
