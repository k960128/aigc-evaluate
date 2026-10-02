package com.kant.llm.eval.agent.production.dao.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
/** D05agent_sample_task事实存储，不开放通用覆盖或删除接口。 */
@Getter @Setter @TableName("agent_sample_task")
public class AgentSampleTaskDO {
    /** 数据库主键。 */ @TableId(type=IdType.AUTO) private Long id;
    /** 生产任务标识。 */ private String taskId;
    /** 服务端租户。 */ private String tenantId;
    /** 认证操作者。 */ private String actorId;
    /** 操作者内创建幂等键。 */ private String idempotencyKey;
    /** 配置请求摘要。 */ private String requestHash;
    /** 任务展示名。 */ private String name;
    /** 原评测活动引用。 */ private String activityId;
    /** 生成与改写的外部批准引用。 */ private String authorizationRef;
    /** 不可变种子及生成配置。 */ private String specJson;
    /** 单批独立预算。 */ private String budgetJson;
    /** 每日计划，可为空。 */ private String scheduleJson;
    /** 计划是否由操作者显式启用。 */ private Boolean scheduleEnabled;
    /** UTC下一个槽位。 */ private LocalDateTime nextFireAt;
    /** 任务控制版本，技术游标不增加。 */ private Long version;
    /** UTC创建时间。 */ private LocalDateTime createdAt;
}
