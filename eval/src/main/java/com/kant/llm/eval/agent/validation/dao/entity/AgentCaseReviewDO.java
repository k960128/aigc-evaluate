package com.kant.llm.eval.agent.validation.dao.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
/** D04agent_case_review持久化事实；无公开通用删除/覆盖接口。 */
@Getter @Setter @TableName("agent_case_review")
public class AgentCaseReviewDO {
    /** 数据库主键。 */ @TableId(type=IdType.AUTO) private Long id;
    /** 候选审核标识。 */ private String reviewId;
    /** 被审核候选。 */ private String candidateId;
    /** 本候选内幂等键。 */ private String idempotencyKey;
    /** 请求内容摘要。 */ private String requestHash;
    /** APPROVE或RETURN。 */ private String decision;
    /** 审核依据。 */ private String reason;
    /** 审核操作者。 */ private String actorId;
    /** 首次受理回执，保留正式版本引用。 */ private String resultJson;
    /** 首次受理时间。 */ private LocalDateTime createdAt;
}
