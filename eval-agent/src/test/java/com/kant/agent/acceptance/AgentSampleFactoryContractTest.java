package com.kant.agent.acceptance;

import com.kant.agent.contract.production.GenerationSpec;
import com.kant.agent.contract.production.ProductionBudget;
import com.kant.agent.service.production.SampleGenerationWorkflow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * D05 样本工厂领域契约的构造不变量测试。
 * 覆盖 GenerationSpec、ProductionBudget 与 SampleGenerationWorkflow，
 * 不依赖 Spring、数据库或真实生成模型。
 */
class AgentSampleFactoryContractTest {

    /** 本组契约用例使用的固定提示词版本；不是运行时生成角色指纹。 */
    private static final String PROMPT_VERSION = "sample-generation-v1";

    // ---------- 不可变生成配置：正常构造、边界拒绝与防御性复制 ----------

    @Test
    @DisplayName("合法不可变生成配置构造成功，字段原样保留")
    void buildsValidGenerationSpec() {
        GenerationSpec spec = validSpec();
        assertEquals("seed-1", spec.seedId());
        assertEquals(1, spec.seedVersion());
        assertEquals("gen-profile-1", spec.generatorProfileRef());
        assertEquals(PROMPT_VERSION, spec.promptVersion());
        assertEquals(3, spec.quantity());
        assertEquals(1, spec.maxRewrites());
    }

    @Test
    @DisplayName("标识类字段按契约去除首尾空白")
    void trimsIdentifierFields() {
        GenerationSpec spec = spec("  seed-1  ", 1, " gen-profile-1 ", PROMPT_VERSION,
                " risk-1 ", " rules-v1 ", 3, List.of("保留虚构场景"), List.of("主题"),
                4, 100, 1, "仅授权虚构场景", "验证文本机制");
        assertEquals("seed-1", spec.seedId());
        assertEquals("gen-profile-1", spec.generatorProfileRef());
        assertEquals("risk-1", spec.riskId());
        assertEquals("rules-v1", spec.ruleSetVersion());
    }

    @Test
    @DisplayName("seedId 空白或超过96字符被拒绝")
    void rejectsInvalidSeedId() {
        assertThrows(IllegalArgumentException.class,
                () -> spec(" ", 1, "gen-profile-1", PROMPT_VERSION, "risk-1", "rules-v1",
                        3, List.of("保留虚构场景"), List.of("主题"), 4, 100, 1, "仅授权虚构场景", "验证文本机制"));
        assertThrows(IllegalArgumentException.class,
                () -> spec("x".repeat(97), 1, "gen-profile-1", PROMPT_VERSION, "risk-1", "rules-v1",
                        3, List.of("保留虚构场景"), List.of("主题"), 4, 100, 1, "仅授权虚构场景", "验证文本机制"));
    }

    @Test
    @DisplayName("种子版本只接受不可变版本1")
    void onlySupportsSeedVersionOne() {
        assertThrows(IllegalArgumentException.class,
                () -> spec("seed-1", 0, "gen-profile-1", PROMPT_VERSION, "risk-1", "rules-v1",
                        3, List.of("保留虚构场景"), List.of("主题"), 4, 100, 1, "仅授权虚构场景", "验证文本机制"));
        assertThrows(IllegalArgumentException.class,
                () -> spec("seed-1", 2, "gen-profile-1", PROMPT_VERSION, "risk-1", "rules-v1",
                        3, List.of("保留虚构场景"), List.of("主题"), 4, 100, 1, "仅授权虚构场景", "验证文本机制"));
    }

    @Test
    @DisplayName("生成角色摘要空白或超过96字符被拒绝")
    void rejectsInvalidGeneratorProfileRef() {
        assertThrows(IllegalArgumentException.class,
                () -> spec("seed-1", 1, " ", PROMPT_VERSION, "risk-1", "rules-v1",
                        3, List.of("保留虚构场景"), List.of("主题"), 4, 100, 1, "仅授权虚构场景", "验证文本机制"));
        assertThrows(IllegalArgumentException.class,
                () -> spec("seed-1", 1, "x".repeat(97), PROMPT_VERSION, "risk-1", "rules-v1",
                        3, List.of("保留虚构场景"), List.of("主题"), 4, 100, 1, "仅授权虚构场景", "验证文本机制"));
    }

    @Test
    @DisplayName("提示词版本只接受 sample-generation-v1")
    void rejectsUnknownPromptVersion() {
        assertThrows(IllegalArgumentException.class,
                () -> spec("seed-1", 1, "gen-profile-1", "other-version", "risk-1", "rules-v1",
                        3, List.of("保留虚构场景"), List.of("主题"), 4, 100, 1, "仅授权虚构场景", "验证文本机制"));
    }

    @Test
    @DisplayName("风险目录与规则版本空白或超长被拒绝")
    void rejectsInvalidRiskAndRuleRefs() {
        assertThrows(IllegalArgumentException.class,
                () -> spec("seed-1", 1, "gen-profile-1", PROMPT_VERSION, " ", "rules-v1",
                        3, List.of("保留虚构场景"), List.of("主题"), 4, 100, 1, "仅授权虚构场景", "验证文本机制"));
        assertThrows(IllegalArgumentException.class,
                () -> spec("seed-1", 1, "gen-profile-1", PROMPT_VERSION, "risk-1", "x".repeat(129),
                        3, List.of("保留虚构场景"), List.of("主题"), 4, 100, 1, "仅授权虚构场景", "验证文本机制"));
    }

    @Test
    @DisplayName("根槽位数量边界为1至500")
    void boundsQuantity() {
        assertThrows(IllegalArgumentException.class,
                () -> spec("seed-1", 1, "gen-profile-1", PROMPT_VERSION, "risk-1", "rules-v1",
                        0, List.of("保留虚构场景"), List.of("主题"), 4, 100, 1, "仅授权虚构场景", "验证文本机制"));
        assertThrows(IllegalArgumentException.class,
                () -> spec("seed-1", 1, "gen-profile-1", PROMPT_VERSION, "risk-1", "rules-v1",
                        501, List.of("保留虚构场景"), List.of("主题"), 4, 100, 1, "仅授权虚构场景", "验证文本机制"));
        assertEquals(1, validSpec(1, 1).quantity());
        assertEquals(500, validSpec(500, 1).quantity());
    }

    @Test
    @DisplayName("变异要求必须非空、最多10项且每项非空不超过500字符")
    void validatesMutations() {
        assertThrows(IllegalArgumentException.class,
                () -> spec("seed-1", 1, "gen-profile-1", PROMPT_VERSION, "risk-1", "rules-v1",
                        3, List.of(), List.of("主题"), 4, 100, 1, "仅授权虚构场景", "验证文本机制"));
        assertThrows(IllegalArgumentException.class,
                () -> spec("seed-1", 1, "gen-profile-1", PROMPT_VERSION, "risk-1", "rules-v1",
                        3, List.of(" "), List.of("主题"), 4, 100, 1, "仅授权虚构场景", "验证文本机制"));
        List<String> tooMany = new ArrayList<>();
        for (int i = 0; i < 11; i++) tooMany.add("m" + i);
        assertThrows(IllegalArgumentException.class,
                () -> spec("seed-1", 1, "gen-profile-1", PROMPT_VERSION, "risk-1", "rules-v1",
                        3, tooMany, List.of("主题"), 4, 100, 1, "仅授权虚构场景", "验证文本机制"));
        assertThrows(IllegalArgumentException.class,
                () -> spec("seed-1", 1, "gen-profile-1", PROMPT_VERSION, "risk-1", "rules-v1",
                        3, List.of("x".repeat(501)), List.of("主题"), 4, 100, 1, "仅授权虚构场景", "验证文本机制"));
    }

    @Test
    @DisplayName("主题锚点必须非空、最多20项且每项非空不超过128字符")
    void validatesTopicTerms() {
        assertThrows(IllegalArgumentException.class,
                () -> spec("seed-1", 1, "gen-profile-1", PROMPT_VERSION, "risk-1", "rules-v1",
                        3, List.of("保留虚构场景"), List.of(), 4, 100, 1, "仅授权虚构场景", "验证文本机制"));
        assertThrows(IllegalArgumentException.class,
                () -> spec("seed-1", 1, "gen-profile-1", PROMPT_VERSION, "risk-1", "rules-v1",
                        3, List.of("保留虚构场景"), List.of(" "), 4, 100, 1, "仅授权虚构场景", "验证文本机制"));
        List<String> tooMany = new ArrayList<>();
        for (int i = 0; i < 21; i++) tooMany.add("t" + i);
        assertThrows(IllegalArgumentException.class,
                () -> spec("seed-1", 1, "gen-profile-1", PROMPT_VERSION, "risk-1", "rules-v1",
                        3, List.of("保留虚构场景"), tooMany, 4, 100, 1, "仅授权虚构场景", "验证文本机制"));
        assertThrows(IllegalArgumentException.class,
                () -> spec("seed-1", 1, "gen-profile-1", PROMPT_VERSION, "risk-1", "rules-v1",
                        3, List.of("保留虚构场景"), List.of("x".repeat(129)), 4, 100, 1, "仅授权虚构场景", "验证文本机制"));
    }

    @Test
    @DisplayName("正文长度范围不合法被拒绝")
    void validatesLengthRange() {
        assertThrows(IllegalArgumentException.class,
                () -> spec("seed-1", 1, "gen-profile-1", PROMPT_VERSION, "risk-1", "rules-v1",
                        3, List.of("保留虚构场景"), List.of("主题"), 0, 100, 1, "仅授权虚构场景", "验证文本机制"));
        assertThrows(IllegalArgumentException.class,
                () -> spec("seed-1", 1, "gen-profile-1", PROMPT_VERSION, "risk-1", "rules-v1",
                        3, List.of("保留虚构场景"), List.of("主题"), 200, 100, 1, "仅授权虚构场景", "验证文本机制"));
        assertThrows(IllegalArgumentException.class,
                () -> spec("seed-1", 1, "gen-profile-1", PROMPT_VERSION, "risk-1", "rules-v1",
                        3, List.of("保留虚构场景"), List.of("主题"), 4, 10001, 1, "仅授权虚构场景", "验证文本机制"));
    }

    @Test
    @DisplayName("每个槽位最多两次质量改写，改写次数为0至2")
    void boundsMaxRewrites() {
        assertThrows(IllegalArgumentException.class,
                () -> spec("seed-1", 1, "gen-profile-1", PROMPT_VERSION, "risk-1", "rules-v1",
                        3, List.of("保留虚构场景"), List.of("主题"), 4, 100, -1, "仅授权虚构场景", "验证文本机制"));
        assertThrows(IllegalArgumentException.class,
                () -> spec("seed-1", 1, "gen-profile-1", PROMPT_VERSION, "risk-1", "rules-v1",
                        3, List.of("保留虚构场景"), List.of("主题"), 4, 100, 3, "仅授权虚构场景", "验证文本机制"));
        assertEquals(0, validSpec(0).maxRewrites());
        assertEquals(2, validSpec(2).maxRewrites());
    }

    @Test
    @DisplayName("前置条件与断言说明空白或超过4000字符被拒绝")
    void validatesPreconditionsAndAssertion() {
        assertThrows(IllegalArgumentException.class,
                () -> spec("seed-1", 1, "gen-profile-1", PROMPT_VERSION, "risk-1", "rules-v1",
                        3, List.of("保留虚构场景"), List.of("主题"), 4, 100, 1, " ", "验证文本机制"));
        assertThrows(IllegalArgumentException.class,
                () -> spec("seed-1", 1, "gen-profile-1", PROMPT_VERSION, "risk-1", "rules-v1",
                        3, List.of("保留虚构场景"), List.of("主题"), 4, 100, 1, "仅授权虚构场景", "x".repeat(4001)));
    }

    @Test
    @DisplayName("变异要求与主题锚点列表为防御性不可变副本")
    void copiesListsDefensively() {
        List<String> mutations = new ArrayList<>(List.of("保留虚构场景"));
        List<String> topics = new ArrayList<>(List.of("主题"));
        GenerationSpec spec = spec("seed-1", 1, "gen-profile-1", PROMPT_VERSION, "risk-1", "rules-v1",
                3, mutations, topics, 4, 100, 1, "仅授权虚构场景", "验证文本机制");
        assertThrows(UnsupportedOperationException.class, () -> spec.mutations().add("新变异"));
        assertThrows(UnsupportedOperationException.class, () -> spec.topicTerms().add("新锚点"));
        mutations.add("外部改动");
        assertEquals(1, spec.mutations().size());
    }

    // ---------- 独立生产预算：正数上限、固定期限及非负账本 ----------

    @Test
    @DisplayName("合法独立生产预算构造成功，正数字段保留")
    void buildsValidBudget() {
        ProductionBudget budget = new ProductionBudget(6, 30000, 3600);
        assertEquals(6, budget.maxAgentCalls());
        assertEquals(30000, budget.maxTokens());
        assertEquals(3600, budget.maxDurationSeconds());
    }

    @Test
    @DisplayName("生成调用数与Token预算必须为正")
    void requiresPositiveBudgetCounters() {
        assertThrows(IllegalArgumentException.class, () -> new ProductionBudget(0, 30000, 3600));
        assertThrows(IllegalArgumentException.class, () -> new ProductionBudget(-1, 30000, 3600));
        assertThrows(IllegalArgumentException.class, () -> new ProductionBudget(6, 0, 3600));
        assertThrows(IllegalArgumentException.class, () -> new ProductionBudget(6, -1, 3600));
    }

    @Test
    @DisplayName("运行期限为1至86400秒，恢复不延长")
    void boundsDurationSeconds() {
        assertThrows(IllegalArgumentException.class, () -> new ProductionBudget(6, 30000, 0));
        assertThrows(IllegalArgumentException.class, () -> new ProductionBudget(6, 30000, 86401));
        assertEquals(1, new ProductionBudget(6, 30000, 1).maxDurationSeconds());
        assertEquals(86400, new ProductionBudget(6, 30000, 86400).maxDurationSeconds());
    }

    @Test
    @DisplayName("使用账本三个计数均不可为负")
    void requiresNonNegativeUsage() {
        assertDoesNotThrow(() -> new ProductionBudget.Usage(0, 0, 0));
        assertEquals(6, new ProductionBudget.Usage(6, 100, 50).agentCalls());
        assertThrows(IllegalArgumentException.class, () -> new ProductionBudget.Usage(-1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new ProductionBudget.Usage(0, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> new ProductionBudget.Usage(0, 0, -1));
    }

    // ---------- 提示词契约：版本和输出边界，不代表实际模型一定遵循 ----------

    @Test
    @DisplayName("生成工作流版本与生成配置允许的提示词版本一致")
    void workflowVersionMatchesSpecPromptVersion() {
        assertEquals(PROMPT_VERSION, SampleGenerationWorkflow.VERSION);
    }

    @Test
    @DisplayName("系统提示词固定约束：不调用目标、只返回JSON、拒绝走REFUSED")
    void systemPromptFreezesSafetyConstraints() {
        String prompt = SampleGenerationWorkflow.SYSTEM_PROMPT;
        assertTrue(prompt.contains("不调用目标、网络、数据库或工具"));
        assertTrue(prompt.contains("只返回一个JSON对象"));
        assertTrue(prompt.contains("GENERATED"));
        assertTrue(prompt.contains("REFUSED"));
        assertTrue(prompt.contains("质量反馈只能用于有限改写"));
    }

    // ---------- 虚构数据构造助手：只创建内存契约，不运行生产任务 ----------

    /** 默认三个根槽位、最多一次改写的合法配置。 */
    private static GenerationSpec validSpec() {
        return validSpec(1);
    }

    /** 只改变改写上限，其他条件固定，用于隔离边界判断。 */
    private static GenerationSpec validSpec(int maxRewrites) {
        return spec("seed-1", 1, "gen-profile-1", PROMPT_VERSION, "risk-1", "rules-v1",
                3, List.of("保留虚构场景"), List.of("主题"),
                4, 100, maxRewrites, "仅授权虚构场景", "验证文本机制，不声明漏洞");
    }

    /** 同时指定根槽位数与改写上限，不能将槽位数解释为合格样本数。 */
    private static GenerationSpec validSpec(int quantity, int maxRewrites) {
        return spec("seed-1", 1, "gen-profile-1", PROMPT_VERSION, "risk-1", "rules-v1",
                quantity, List.of("保留虚构场景"), List.of("主题"),
                4, 100, maxRewrites, "仅授权虚构场景", "验证文本机制，不声明漏洞");
    }

    /** 直接传递测试参数给领域构造器，保留非法输入以观察构造约束，不在助手中修正。 */
    private static GenerationSpec spec(String seedId, long seedVersion, String profile, String prompt,
                                       String riskId, String rules, int quantity,
                                       List<String> mutations, List<String> topics,
                                       int minLength, int maxLength, int maxRewrites,
                                       String preconditions, String assertion) {
        return new GenerationSpec(seedId, seedVersion, profile, prompt, riskId, rules,
                quantity, mutations, topics, minLength, maxLength, maxRewrites, preconditions, assertion);
    }
}
