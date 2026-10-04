package com.kant.llm.eval.agent.production.dao.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
/**
 * 一次已确认生成或改写的候选事实，来源operationId与原计划、批次共同可追溯。
 * 正文和父链不覆盖；status是准入状态，qualityJson是质量报告，不能与生成操作成功混用。
 * 对应agent_sample_candidate表，不向外开放通用覆盖或删除接口；数据库时间约定为UTC微秒精度。
 */
@Getter @Setter @TableName("agent_sample_candidate")
public class AgentSampleCandidateDO {
    /** 数据库主键。 */ @TableId(type=IdType.AUTO) private Long id;
    /** 从来源操作ID派生的候选标识，同一操作不会因恢复生成不同候选身份。 */ private String candidateId;
    /** 所属任务。 */ private String taskId;
    /** 所属批次。 */ private String runId;
    /** 服务端租户。 */ private String tenantId;
    /** 操作者。 */ private String actorId;
    /** 来源生成操作。 */ private String operationId;
    /** 0基根样本槽位。 */ private Integer ordinal;
    /** 0为原生成，1或2为质量反馈改写，不是与目标交互的对话轮数。 */ private Integer rewriteRound;
    /** 父候选，血缘不覆盖。 */ private String parentCandidateId;
    /** 专家种子。 */ private String seedId;
    /** 受限原始正文，不按去重标准改写。 */ private String content;
    /** 原始正文SHA-256；拒绝或非法结构导致正文为空时摘要也为空。 */ private String contentHash;
    /** 仅对质量PASS候选计算的规范化摘要，按去重算法生成，不用于替代原文完整性检查。 */ private String normalizedHash;
    /** 重复来源候选。 */ private String duplicateOfCandidateId;
    /** Report的JSON：version检查器版本、outcome质量分类、issues问题列表，不是安全判定。 */ private String qualityJson;
    /** PENDING_REVIEW待审、QUALITY_REJECTED质检拒绝、DUPLICATE重复、RETURNED退回、APPROVED批准。 */ private String status;
    /** 人工审核资源版本，候选形成时0，审核后递增，与计划或批次版本无关。 */ private Long version;
    /** UTC生成时间。 */ private LocalDateTime createdAt;
}
