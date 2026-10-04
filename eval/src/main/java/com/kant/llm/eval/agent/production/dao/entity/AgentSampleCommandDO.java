package com.kant.llm.eval.agent.production.dao.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
/**
 * 计划启停或批次停止的幂等控制回执，不是发给模型的生成命令。
 * resourceId与idempotencyKey确定首次命令，状态变更与回执在同一事务内提交。
 * 对应agent_sample_command表，不向外开放通用覆盖或删除接口；数据库时间约定为UTC微秒精度。
 */
@Getter @Setter @TableName("agent_sample_command")
public class AgentSampleCommandDO {
    /** 数据库主键。 */ @TableId(type=IdType.AUTO) private Long id;
    /** 任务或批次控制资源。 */ private String resourceId;
    /** 资源内命令幂等键。 */ private String idempotencyKey;
    /** 完整请求摘要。 */ private String requestHash;
    /** 首次命令回执。 */ private String resultJson;
}
