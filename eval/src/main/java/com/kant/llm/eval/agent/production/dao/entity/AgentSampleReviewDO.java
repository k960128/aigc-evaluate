package com.kant.llm.eval.agent.production.dao.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
/** D05agent_sample_review事实存储，不开放通用覆盖或删除接口。 */
@Getter @Setter @TableName("agent_sample_review")
public class AgentSampleReviewDO {
    /** 数据库主键。 */ @TableId(type=IdType.AUTO) private Long id;
    /** 追加审核记录。 */ private String reviewId;
    /** 候选。 */ private String candidateId;
    /** 候选内审核幂等键。 */ private String idempotencyKey;
    /** 审核请求摘要。 */ private String requestHash;
    /** 人工审核依据。 */ private String reason;
    /** APPROVE RETURN。 */ private String decision;
    /** 首次审核回执。 */ private String resultJson;
    /** UTC受理时间。 */ private LocalDateTime createdAt;
}
