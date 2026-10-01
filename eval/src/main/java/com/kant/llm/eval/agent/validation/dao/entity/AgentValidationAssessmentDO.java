package com.kant.llm.eval.agent.validation.dao.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
/** D04agent_validation_assessment持久化事实；无公开通用删除/覆盖接口。 */
@Getter @Setter @TableName("agent_validation_assessment")
public class AgentValidationAssessmentDO {
    /** 数据库主键。 */ @TableId(type=IdType.AUTO) private Long id;
    /** 追加判定标识。 */ private String assessmentId;
    /** 独立验证运行。 */ private String runId;
    /** 含scope、规则、judgeVersion和证据引用的判定。 */ private String assessmentJson;
}
