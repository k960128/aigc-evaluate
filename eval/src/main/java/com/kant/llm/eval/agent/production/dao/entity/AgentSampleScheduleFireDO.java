package com.kant.llm.eval.agent.production.dao.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
/**
 * 每日调度触发或遗漏的审计事实，保留原计划UTC槽位而非当前执行时刻。
 * MISSED_RANGE描述较早遗漏区间，不表示期间每天都执行过；遗漏时runId为空。
 * 对应agent_sample_schedule_fire表，不向外开放通用覆盖或删除接口；数据库时间约定为UTC微秒精度。
 */
@Getter @Setter @TableName("agent_sample_schedule_fire")
public class AgentSampleScheduleFireDO {
    /** 数据库主键。 */ @TableId(type=IdType.AUTO) private Long id;
    /** 生产计划任务。 */ private String taskId;
    /** UTC计划槽位或遗漏区间起点。 */ private LocalDateTime plannedFor;
    /** QUEUED关联批次、MISSED单槽位遗漏、MISSED_RANGE较早区间遗漏。 */ private String kind;
    /** 创建批次，可为空。 */ private String runId;
    /** 时区、漏触发策略/原因或from至before区间的JSON；遗漏区间包含起点但不包含终点。 */ private String detailsJson;
    /** UTC实际处理时间。 */ private LocalDateTime createdAt;
}
