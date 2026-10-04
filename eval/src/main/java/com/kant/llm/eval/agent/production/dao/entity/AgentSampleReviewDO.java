package com.kant.llm.eval.agent.production.dao.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
/**
 * 人工审核决定和首次回执；审批、候选状态及正式资产在同一事务内保存。
 * 同候选同幂等键只能回放原请求；审核人、批准资产ID与候选版本保存在resultJson中。
 * 对应agent_sample_review表，不向外开放通用覆盖或删除接口；数据库时间约定为UTC微秒精度。
 */
@Getter @Setter @TableName("agent_sample_review")
public class AgentSampleReviewDO {
    /** 数据库主键。 */ @TableId(type=IdType.AUTO) private Long id;
    /** 追加审核记录。 */ private String reviewId;
    /** 候选。 */ private String candidateId;
    /** 候选内审核幂等键。 */ private String idempotencyKey;
    /** 审核请求摘要。 */ private String requestHash;
    /** 人工审核依据。 */ private String reason;
    /** APPROVE批准并形成正式资产；RETURN退回且不自动改写。 */ private String decision;
    /** Review首次回执，含审核主体、候选受理版本及可空正式资产ID/版本。 */ private String resultJson;
    /** UTC受理时间。 */ private LocalDateTime createdAt;
}
