package com.kant.agent.acceptance;

import com.kant.agent.contract.production.GenerationSpec;
import com.kant.agent.contract.production.ProductionSchedule;
import com.kant.agent.port.production.SampleGenerationPort;
import com.kant.agent.service.production.SampleQualityWorkflow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** D05确定性质检、去重规范和计划时区测试；不调用生成模型或调度后台。 */
class AgentProductionWorkflowTest {
    private final SampleQualityWorkflow quality = new SampleQualityWorkflow();

    @Test
    @DisplayName("合法虚构样本只得到质量PASS，不代表目标风险已确认")
    void passesLiteralQualityCheck() {
        var report = quality.inspect(spec(2), result("GENERATED", "主题 虚构样本"));
        assertEquals("PASS", report.outcome());
        assertEquals(SampleQualityWorkflow.VERSION, report.version());
        assertTrue(report.issues().isEmpty());
    }

    @Test
    @DisplayName("生成拒绝或结构非法直接REJECT，不自动绕过拒绝")
    void rejectsRefusalAndInvalidStructure() {
        assertEquals("REJECT", quality.inspect(spec(2), result("REFUSED", null)).outcome());
        assertEquals("REJECT", quality.inspect(spec(2), result("INVALID", null)).outcome());
    }

    @Test
    @DisplayName("主题或长度不足需要改写，不能自动批准")
    void requiresRevisionForQualityGaps() {
        assertEquals("REVISE", quality.inspect(spec(2), result("GENERATED", "没有指定锚点")).outcome());
        assertEquals("REVISE", quality.inspect(spec(2), result("GENERATED", "主题")).outcome());
    }

    @Test
    @DisplayName("正文为空或存在不支持的控制字符时拒绝")
    void rejectsEmptyAndControlCharacters() {
        assertEquals("REJECT", quality.inspect(spec(2), result("GENERATED", " \n ")).outcome());
        assertEquals("REJECT", quality.inspect(spec(2), result("GENERATED", "主题正文\u0001")).outcome());
    }

    @Test
    @DisplayName("规范去重合并全半角、大小写和普通空白，不改写原文")
    void normalizesOnlyDedupKey() {
        String original = "  ＡＢＣ\t主题\n ";
        assertEquals(quality.normalized("abc 主题"), quality.normalized(original));
        assertEquals("  ＡＢＣ\t主题\n ", original);
    }

    @Test
    @DisplayName("每个槽位最多允许两次质量改写")
    void boundsRewriteRounds() {
        assertEquals(2, spec(2).maxRewrites());
        assertThrows(IllegalArgumentException.class, () -> spec(3));
    }

    @Test
    @DisplayName("上海每日计划按原时区转换UTC且结束后没有下一槽位")
    void computesShanghaiSlots() {
        var schedule = schedule("Asia/Shanghai", "2026-10-01", "2026-10-02", LocalTime.of(2, 0));
        Instant first = Instant.parse("2026-09-30T18:00:00Z");
        Instant last = Instant.parse("2026-10-01T18:00:00Z");
        assertEquals(first, schedule.at(LocalDate.parse("2026-10-01")));
        assertEquals(last, schedule.next(first));
        assertNull(schedule.next(last));
        assertTrue(schedule.isSlot(first));
        assertFalse(schedule.isSlot(first.plusSeconds(1)));
    }

    @Test
    @DisplayName("FIRE_LATEST选最新到期槽位，不逐日补发模型调用")
    void selectsLatestDueSlot() {
        var schedule = schedule("Asia/Shanghai", "2026-10-01", "2026-10-10", LocalTime.of(2, 0));
        assertNull(schedule.latestDue(Instant.parse("2026-09-29T18:00:00Z")));
        assertEquals(Instant.parse("2026-10-04T18:00:00Z"),
                schedule.latestDue(Instant.parse("2026-10-05T10:00:00Z")));
        assertEquals(Instant.parse("2026-10-09T18:00:00Z"),
                schedule.latestDue(Instant.parse("2026-10-20T10:00:00Z")));
    }

    @Test
    @DisplayName("DST缺失时刻顺延，重复时刻选择较早偏移")
    void handlesDaylightSavingTime() {
        var spring = schedule("America/New_York", "2026-03-08", "2026-03-08", LocalTime.of(2, 30));
        assertEquals(Instant.parse("2026-03-08T07:30:00Z"), spring.at(LocalDate.parse("2026-03-08")));
        var autumn = schedule("America/New_York", "2026-11-01", "2026-11-01", LocalTime.of(1, 30));
        assertEquals(Instant.parse("2026-11-01T05:30:00Z"), autumn.at(LocalDate.parse("2026-11-01")));
        assertFalse(autumn.isSlot(Instant.parse("2026-11-01T06:30:00Z")));
    }

    @Test
    @DisplayName("计划必须有界且精确到分钟")
    void rejectsInvalidScheduleRange() {
        assertThrows(IllegalArgumentException.class,
                () -> schedule("Asia/Shanghai", "2026-10-02", "2026-10-01", LocalTime.of(2, 0)));
        assertThrows(IllegalArgumentException.class,
                () -> schedule("Asia/Shanghai", "2026-01-01", "2027-01-02", LocalTime.of(2, 0)));
        assertThrows(IllegalArgumentException.class,
                () -> schedule("Asia/Shanghai", "2026-10-01", "2026-10-02", LocalTime.of(2, 0, 1)));
    }

    private GenerationSpec spec(int rewrites) {
        return new GenerationSpec("seed-test", 1, "generator-test", "sample-generation-v1",
                "fictional-risk", "rules-v1", 1, List.of("保留虚构场景，改变表达"), List.of("主题"),
                4, 100, rewrites, "仅用于授权的虚构场景", "只验证文本机制，不声明漏洞");
    }

    private SampleGenerationPort.Result result(String status, String content) {
        return new SampleGenerationPort.Result(status, content, "机制测试", 10, 10, "ESTIMATED");
    }

    private ProductionSchedule schedule(String zone, String start, String end, LocalTime time) {
        return new ProductionSchedule(zone, time, LocalDate.parse(start), LocalDate.parse(end),
                ProductionSchedule.MisfirePolicy.FIRE_LATEST, 60);
    }
}
