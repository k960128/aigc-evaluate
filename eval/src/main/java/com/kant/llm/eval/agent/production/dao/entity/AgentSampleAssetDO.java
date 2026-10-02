package com.kant.llm.eval.agent.production.dao.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
/** D05agent_sample_asset事实存储，不开放通用覆盖或删除接口。 */
@Getter @Setter @TableName("agent_sample_asset")
public class AgentSampleAssetDO {
    /** 数据库主键。 */ @TableId(type=IdType.AUTO) private Long id;
    /** 审核启用的正式单消息用例。 */ private String caseId;
    /** 不可变版本，首版1。 */ private Long caseVersion;
    /** 来源候选。 */ private String candidateId;
    /** 服务端租户。 */ private String tenantId;
    /** 操作者。 */ private String actorId;
    /** 审核记录引用。 */ private String approvalRef;
    /** 固定前置条件输入引用断言原规则配置。 */ private String definitionJson;
    /** UTC发布时间。 */ private LocalDateTime createdAt;
}
