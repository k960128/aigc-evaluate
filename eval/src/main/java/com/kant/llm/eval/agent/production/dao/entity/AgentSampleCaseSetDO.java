package com.kant.llm.eval.agent.production.dao.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
/** D05agent_sample_case_set事实存储，不开放通用覆盖或删除接口。 */
@Getter @Setter @TableName("agent_sample_case_set")
public class AgentSampleCaseSetDO {
    /** 数据库主键。 */ @TableId(type=IdType.AUTO) private Long id;
    /** 固定正式用例集。 */ private String caseSetId;
    /** 不可变版本，首版1。 */ private Long caseSetVersion;
    /** 服务端租户。 */ private String tenantId;
    /** 操作者。 */ private String actorId;
    /** 创建幂等键。 */ private String idempotencyKey;
    /** 请求摘要。 */ private String requestHash;
    /** 集合展示名称。 */ private String name;
    /** 精确正式用例版本集合。 */ private String casesJson;
    /** UTC创建时间。 */ private LocalDateTime createdAt;
}
