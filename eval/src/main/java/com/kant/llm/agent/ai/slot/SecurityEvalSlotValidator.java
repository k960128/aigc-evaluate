package com.kant.llm.agent.ai.slot;

import com.kant.llm.agent.ai.constant.SecurityEvalIntent;
import com.kant.llm.agent.ai.model.SecurityEvalIntentRecognitionResult;
import com.kant.llm.agent.ai.model.SecurityEvalSlotValidationResult;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 安全评测意图槽位校验器。
 *
 * <p>该组件位于“意图识别”之后、“Skill Router”之前，用于对大模型抽取结果做后端确定性校验。
 * Prompt 可以尽量识别意图和抽取槽位，但是否允许进入业务执行链路，必须由这里的规则表最终决定。</p>
 *
 * <p>职责边界：</p>
 * <ul>
 *     <li>校验意图是否合法、是否属于安全评测助手范围。</li>
 *     <li>校验每个意图进入后续能力所需的最低槽位是否满足。</li>
 *     <li>在缺槽位时生成稳定的澄清问题。</li>
 * </ul>
 *
 * <p>该组件不调用数据库、不调用 MCP、不执行检索和诊断逻辑，只做路由前校验。</p>
 */
@Component
public class SecurityEvalSlotValidator {

    /**
     * 对安全评测意图识别结果进行槽位校验。
     *
     * <p>这里不会直接信任模型输出的 {@code needClarification} 和 {@code missingSlots}，
     * 而是基于后端维护的意图槽位规则重新计算是否可执行。</p>
     *
     * @param result 安全评测意图识别结果
     * @return 后端可信的槽位校验结果
     */
    public SecurityEvalSlotValidationResult validate(SecurityEvalIntentRecognitionResult result) {
        SecurityEvalIntent intent = SecurityEvalIntent.from(result.intent());

        // 无关请求、越界请求、未知意图不进入任何安全评测执行链路。
        if (!result.related() || intent == SecurityEvalIntent.OUT_OF_SCOPE || intent == SecurityEvalIntent.UNKNOWN) {
            return SecurityEvalSlotValidationResult.notExecutable(intent);
        }

        SecurityEvalIntentRecognitionResult.Entities e = result.entities();

        return switch (intent) {
            // 三层链路诊断至少需要已有结果明细，或用户直接给出待诊断输入/输出。
            case FULL_LINK_TRACE -> requireAny(intent, e,
                    List.of("result_detail_id", "input_text", "model_output"),
                    "请提供评测结果明细ID，或提供待诊断的输入/模型输出内容。");

            // 规则与特征溯源必须能定位到某个规则、特征、词库项或风险小类。
            case RULE_FEATURE_TRACE -> requireAny(intent, e,
                    List.of("rule_id", "feature_id", "keyword_id", "risk_details_id", "risk_details_name"),
                    "请提供规则ID、特征ID、词库关键词ID或风险小类信息。");

            // 误杀漏判分析至少需要一条可定位的结果，或待分析的输入/模型输出。
            case MISJUDGEMENT_ANALYSIS -> requireAny(intent, e,
                    List.of("result_detail_id", "input_text", "model_output"),
                    "请提供评测结果明细ID，或提供疑似误杀/漏判的输入与模型输出。");

            // 相似案例检索可以基于结果明细、文本内容、风险小类或关键词发起。
            case SIMILAR_CASE_RETRIEVAL -> requireAny(intent, e,
                    List.of("result_detail_id", "input_text", "model_output", "risk_details_id", "risk_details_name", "keywords"),
                    "请提供待检索的样本内容、评测结果明细ID、风险小类或关键词。");

            // 资产查询必须至少能识别查询对象，否则后续 Text2SQL/MCP 很难稳定生成查询。
            case ASSET_DATA_QUERY -> requireAny(intent, e,
                    List.of("risk_details_id", "risk_details_name", "category_id", "category_name", "rule_id", "feature_id", "keyword_id", "keywords"),
                    "请说明要查询的资产对象，例如风险小类、规则、词库、特征库、评测结果或统计指标。");

            // 特征提取需要有可分析文本、模型输出或用户已经标出的候选特征片段。
            case FEATURE_EXTRACTION -> requireAny(intent, e,
                    List.of("input_text", "model_output", "candidate_features"),
                    "请提供需要提取风险特征的文本、模型输出或候选特征片段。");

            // 报告辅助必须基于已有任务、批次或结果明细生成，避免凭空生成评测结论。
            case REPORT_ASSISTANCE -> requireAny(intent, e,
                    List.of("task_id", "task_detail_id", "result_detail_id"),
                    "请提供评测任务ID、执行批次ID或评测结果明细ID。");

            // 知识问答和普通咨询本身即可由用户问题承载，不强制要求额外结构化槽位。
            case COMPLIANCE_QA, GENERAL_CHAT -> SecurityEvalSlotValidationResult.pass(intent);

            default -> SecurityEvalSlotValidationResult.notExecutable(intent);
        };
    }

    /**
     * 校验一组候选槽位中是否至少存在一个有效值。
     *
     * <p>适用于“任一定位信息即可执行”的意图，例如链路诊断既可以用 result_detail_id，
     * 也可以用用户直接提供的 input_text/model_output。</p>
     *
     * @param intent                当前安全评测意图
     * @param entities              模型抽取出的实体槽位
     * @param requiredAnySlots      至少需要命中一个的槽位名称列表
     * @param clarificationQuestion 缺失槽位时返回给用户的澄清问题
     * @return 校验通过或需要澄清的结果
     */
    private SecurityEvalSlotValidationResult requireAny(
            SecurityEvalIntent intent,
            SecurityEvalIntentRecognitionResult.Entities entities,
            List<String> requiredAnySlots,
            String clarificationQuestion
    ) {
        if (entities != null && requiredAnySlots.stream().anyMatch(slot -> hasSlot(entities, slot))) {
            return SecurityEvalSlotValidationResult.pass(intent);
        }
        return SecurityEvalSlotValidationResult.clarify(intent, requiredAnySlots, clarificationQuestion);
    }

    /**
     * 判断指定槽位是否已经具备有效值。
     *
     * <p>这里集中维护槽位名称与 {@link SecurityEvalIntentRecognitionResult.Entities} 字段之间的映射，
     * 避免在各个意图规则里散落空值判断逻辑。</p>
     *
     * @param e    安全评测实体槽位对象
     * @param slot 槽位名称，必须与 {@code SecurityEvalIntentRecognitionResult.Entities} 中的字段语义一致
     * @return true 表示该槽位存在有效值；false 表示为空或未知槽位
     */
    private boolean hasSlot(SecurityEvalIntentRecognitionResult.Entities e, String slot) {
        return switch (slot) {
            case "input_text" -> hasText(e.input_text());
            case "model_output" -> hasText(e.model_output());
            case "risk_details_id" -> e.risk_details_id() != null;
            case "risk_details_name" -> hasText(e.risk_details_name());
            case "category_id" -> e.category_id() != null;
            case "category_name" -> hasText(e.category_name());
            case "task_id" -> e.task_id() != null;
            case "task_detail_id" -> e.task_detail_id() != null;
            case "result_detail_id" -> e.result_detail_id() != null;
            case "rule_id" -> e.rule_id() != null;
            case "feature_id" -> e.feature_id() != null;
            case "keyword_id" -> e.keyword_id() != null;
            case "candidate_features" -> e.candidate_features() != null && !e.candidate_features().isEmpty();
            case "keywords" -> e.keywords() != null && !e.keywords().isEmpty();
            default -> false;
        };
    }

    /**
     * 判断文本槽位是否为有效文本。
     *
     * @param value 待校验文本
     * @return true 表示非 null 且包含非空白字符
     */
    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
