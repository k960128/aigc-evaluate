package com.kant.llm.eval.agent.production.dao.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
/**
 * 冻结的生产计划定义，以及可变的调度开关、游标和控制版本。
 * spec/budget/schedule不随重跑改变；重置创建新taskId，旧计划、批次和审批引用原样保留。
 * 对应agent_sample_task表，不向外开放通用覆盖或删除接口；数据库时间约定为UTC微秒精度。
 */
@Getter @Setter @TableName("agent_sample_task")
public class AgentSampleTaskDO {
    /** 数据库主键。 */ @TableId(type=IdType.AUTO) private Long id;
    /** 生产任务标识。 */ private String taskId;
    /** 服务端租户。 */ private String tenantId;
    /** 认证操作者。 */ private String actorId;
    /** 操作者内创建幂等键。 */ private String idempotencyKey;
    /** 普通创建请求或含来源计划ID的重置请求摘要，用于拒绝同键异参。 */ private String requestHash;
    /** 任务展示名。 */ private String name;
    /** 原评测活动引用。 */ private String activityId;
    /** 生成与改写的外部批准引用，目前仅保存，不验证审批有效期或撤销状态。 */ private String authorizationRef;
    /** 不可变种子及生成配置。 */ private String specJson;
    /** ProductionBudget的JSON：最大调用次数、Token上限和固定期限；不是整个计划累计额度。 */ private String budgetJson;
    /** 每日计划，可为空。 */ private String scheduleJson;
    /** 计划是否由操作者显式启用。 */ private Boolean scheduleEnabled;
    /** UTC下一个原计划槽位；禁用调度时保留，重新启用后按原漏触发策略处理。 */ private LocalDateTime nextFireAt;
    /** 计划控制版本，创建为0；启停及范围结束禁用时递增，普通游标推进不递增。 */ private Long version;
    /** UTC创建时间。 */ private LocalDateTime createdAt;
}
