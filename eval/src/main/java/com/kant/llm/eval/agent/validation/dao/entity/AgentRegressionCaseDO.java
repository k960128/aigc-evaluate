package com.kant.llm.eval.agent.validation.dao.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
/** D04agent_regression_case持久化事实；无公开通用删除/覆盖接口。 */
@Getter @Setter @TableName("agent_regression_case")
public class AgentRegressionCaseDO {
    /** 数据库主键。 */ @TableId(type=IdType.AUTO) private Long id;
    /** 正式用例标识。 */ private String caseId;
    /** 不可变版本。 */ private Long caseVersion;
    /** 来源候选。 */ private String candidateId;
    /** 来源根发现。 */ private String findingId;
    /** 服务端租户。 */ private String tenantId;
    /** 服务端归属操作者。 */ private String actorId;
    /** 冻结步骤、前置条件与原规则。 */ private String definitionJson;
    /** 候选审核记录引用。 */ private String approvalRef;
    /** 正式版本创建时间。 */ private LocalDateTime createdAt;
}
