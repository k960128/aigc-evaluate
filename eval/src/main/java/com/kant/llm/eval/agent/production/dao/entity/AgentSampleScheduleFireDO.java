package com.kant.llm.eval.agent.production.dao.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
/** D05agent_sample_schedule_fire事实存储，不开放通用覆盖或删除接口。 */
@Getter @Setter @TableName("agent_sample_schedule_fire")
public class AgentSampleScheduleFireDO {
    /** 数据库主键。 */ @TableId(type=IdType.AUTO) private Long id;
    /** 生产计划任务。 */ private String taskId;
    /** UTC计划槽位或遗漏区间起点。 */ private LocalDateTime plannedFor;
    /** QUEUED MISSED MISSED_RANGE。 */ private String kind;
    /** 创建批次，可为空。 */ private String runId;
    /** 时区和漏触发区间等审计。 */ private String detailsJson;
    /** UTC实际处理时间。 */ private LocalDateTime createdAt;
}
