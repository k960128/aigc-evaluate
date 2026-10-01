package com.kant.llm.eval.agent.validation.dao.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
/** D04agent_validation_occurrence持久化事实；无公开通用删除/覆盖接口。 */
@Getter @Setter @TableName("agent_validation_occurrence")
public class AgentValidationOccurrenceDO {
    /** 数据库主键。 */ @TableId(type=IdType.AUTO) private Long id;
    /** 新验证命中发生标识。 */ private String occurrenceId;
    /** 关联根发现。 */ private String findingId;
    /** 新验证运行。 */ private String runId;
    /** 命中固定用例的0基序号；同一次会话前缀多次命中只计一次发生。 */ private Integer caseIndex;
    /** 新判定引用。 */ private String assessmentId;
    /** 本次实际固定目标快照。 */ private String targetSnapshotRef;
    /** 发生时间。 */ private LocalDateTime createdAt;
}
