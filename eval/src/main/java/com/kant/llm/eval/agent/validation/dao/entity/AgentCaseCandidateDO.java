package com.kant.llm.eval.agent.validation.dao.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
/** D04agent_case_candidate持久化事实；无公开通用删除/覆盖接口。 */
@Getter @Setter @TableName("agent_case_candidate")
public class AgentCaseCandidateDO {
    /** 数据库主键。 */ @TableId(type=IdType.AUTO) private Long id;
    /** 候选标识。 */ private String candidateId;
    /** 当前根发现。 */ private String findingId;
    /** 服务端租户。 */ private String tenantId;
    /** 服务端操作者。 */ private String actorId;
    /** 本发现内创建幂等键。 */ private String idempotencyKey;
    /** 请求摘要。 */ private String requestHash;
    /** 固定输入索引、前置条件与断言。 */ private String definitionJson;
    /** PENDING_REVIEW、RETURNED或APPROVED。 */ private String status;
    /** 候选控制版本。 */ private Long version;
    /** 修订既有正式用例时的标识。 */ private String existingCaseId;
    /** 修订依据的正式版本。 */ private Long baseCaseVersion;
    /** 首次创建时间。 */ private LocalDateTime createdAt;
}
