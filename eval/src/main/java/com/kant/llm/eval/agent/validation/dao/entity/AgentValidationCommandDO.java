package com.kant.llm.eval.agent.validation.dao.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Getter;
import lombok.Setter;
/** 追加式停止命令及首次回执，不用单个运行字段覆盖历史幂等键。 */
@Getter @Setter @TableName("agent_validation_command")
public class AgentValidationCommandDO {
    /** 数据库主键。 */ @TableId(type=IdType.AUTO) private Long id;
    /** 所属验证运行。 */ private String runId;
    /** 运行内幂等键。 */ private String idempotencyKey;
    /** 完整命令摘要。 */ private String requestHash;
    /** 首次受理回执，不随运行状态改变。 */ private String resultJson;
}
