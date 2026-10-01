package com.kant.llm.eval.agent.production.dao.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
/** D05agent_sample_candidate事实存储，不开放通用覆盖或删除接口。 */
@Getter @Setter @TableName("agent_sample_candidate")
public class AgentSampleCandidateDO {
    /** 数据库主键。 */ @TableId(type=IdType.AUTO) private Long id;
    /** 候选标识。 */ private String candidateId;
    /** 所属任务。 */ private String taskId;
    /** 所属批次。 */ private String runId;
    /** 服务端租户。 */ private String tenantId;
    /** 操作者。 */ private String actorId;
    /** 来源生成操作。 */ private String operationId;
    /** 0基根样本槽位。 */ private Integer ordinal;
    /** 质量改写轮次。 */ private Integer rewriteRound;
    /** 父候选，血缘不覆盖。 */ private String parentCandidateId;
    /** 专家种子。 */ private String seedId;
    /** 受限原始正文，不按去重标准改写。 */ private String content;
    /** 正文原文摘要。 */ private String contentHash;
    /** 版本化规范去重摘要。 */ private String normalizedHash;
    /** 重复来源候选。 */ private String duplicateOfCandidateId;
    /** 独立质检版本结论原因。 */ private String qualityJson;
    /** PENDING_REVIEW QUALITY_REJECTED DUPLICATE RETURNED APPROVED。 */ private String status;
    /** 人工审核资源版本。 */ private Long version;
    /** UTC生成时间。 */ private LocalDateTime createdAt;
}
