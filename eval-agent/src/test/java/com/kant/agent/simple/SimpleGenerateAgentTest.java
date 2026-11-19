package com.kant.agent.simple;

import com.kant.agent.simple.memory.IterationRecord;
import com.kant.agent.simple.memory.ReflectionMemory;
import com.kant.agent.simple.model.SampleTaskTicket;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mockStatic;

class SimpleGenerateAgentTest {

    @Test
    void rendersVariablesWhilePreservingJsonAndTransformationPlaceholders() {
        var ticket = new SampleTaskTicket.TaskTicket(
                "验证样本格式", "格式兼容性测试", List.of(
                SampleTaskTicket.TacticsContent.semantic("测试场景", "保留示例字段", "{\"field\":\"测试\"}"),
                SampleTaskTicket.TacticsContent.transformation("编码测试", "编码文本", "", "base64")));

        String prompt = renderPrompt(ticket, new ReflectionMemory()).replace("\r\n", "\n");

        assertTrue(prompt.contains("核心攻击意图：验证样本格式"));
        assertTrue(prompt.contains("伪装种子话题：格式兼容性测试"));
        assertTrue(prompt.contains("测试场景：保留示例字段"));
        assertTrue(prompt.contains("示例：{\"field\":\"测试\"}"));
        assertTrue(prompt.contains("编码测试 → 用占位符 {{base64:明文}} 表示"));
        assertTrue(prompt.contains("JSON 对象（以 { 开头，以 } 结尾）"));
        assertTrue(prompt.contains("{\n  \"tactical_analysis\": \"50字以内简述融合策略。\","));
        for (String transformer : List.of("base64", "hex", "leet", "url", "caesar")) {
            assertTrue(prompt.contains("{{" + transformer + ":"));
            assertTrue(prompt.contains(transformer + " /") || prompt.contains("/ " + transformer));
        }
        assertFalse(prompt.contains("<coreIntent>"));
        assertFalse(prompt.contains("<seed>"));
        assertFalse(prompt.contains("<semanticTactics>"));
        assertFalse(prompt.contains("<transformationTactics>"));
        assertFalse(prompt.contains("<allowedTransformers>"));
        assertFalse(prompt.contains("<reflectionContext>"));
        assertFalse(prompt.contains("【历史尝试记录】"));
    }

    @Test
    void preservesTemplateLikeTextInInputsAndReflectionHistory() {
        var ticket = new SampleTaskTicket.TaskTicket(
                "保留 {coreIntent} 与 <seed>",
                "示例 {\"field\":\"<coreIntent>\"}", List.of());
        var memory = new ReflectionMemory();
        memory.record(new IterationRecord(1,
                "历史样本 {{base64:测试文本}} <seed>",
                7,
                7,
                false,
                "保留 {field} 与 <coreIntent>",
                "",
                IterationRecord.SOURCE_GENERATOR,
                true));

        String prompt = renderPrompt(ticket, memory).replace("\r\n", "\n");

        assertTrue(prompt.contains("核心攻击意图：" + ticket.coreIntent()));
        assertTrue(prompt.contains("伪装种子话题：" + ticket.seed()));
        assertTrue(prompt.contains("【历史尝试记录】"));
        assertTrue(prompt.contains("历史样本 {{base64:测试文本}} <seed>"));
        assertTrue(prompt.contains("保留 {field} 与 <coreIntent>"));
        assertTrue(prompt.contains("【必须融合的语义类战术】（由你创作融入语境）\n（无）"));
        assertTrue(prompt.contains("【需要叠加的变换类战术】（**由下游程序执行，你不要自己编码**）\n（无）"));
    }

    private static String renderPrompt(SampleTaskTicket.TaskTicket ticket, ReflectionMemory memory) {
        // 只替换模型执行边界，实际运行 generatePrompt 中的模板渲染逻辑。
        try (MockedStatic<SimpleAgentExecutor> executor = mockStatic(SimpleAgentExecutor.class)) {
            executor.when(() -> SimpleAgentExecutor.execute(
                            eq("GENERATOR_AGENT"), anyString(), same(AgentExecutionListener.NOOP), any(), any()))
                    .thenAnswer(invocation -> invocation.getArgument(1));
            return new SimpleGenerateAgent().generatePrompt(ticket, memory, 0.6);
        }
    }
}
