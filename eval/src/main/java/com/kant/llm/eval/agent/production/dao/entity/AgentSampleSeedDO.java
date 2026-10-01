package com.kant.llm.eval.agent.production.dao.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
/** D05agent_sample_seed事实存储，不开放通用覆盖或删除接口。 */
@Getter @Setter @TableName("agent_sample_seed")
public class AgentSampleSeedDO {
    /** 数据库主键。 */ @TableId(type=IdType.AUTO) private Long id;
    /** 不可变专家种子标识。 */ private String seedId;
    /** 服务端租户。 */ private String tenantId;
    /** 认证操作者。 */ private String actorId;
    /** 操作者内创建幂等键。 */ private String idempotencyKey;
    /** 请求摘要。 */ private String requestHash;
    /** 种子展示名。 */ private String name;
    /** 受限种子原文，仅授权详情读取。 */ private String content;
    /** 原文SHA256。 */ private String contentHash;
    /** 专家案例来源引用，不自动抓取外部URL。 */ private String sourceRef;
    /** UTC创建时间。 */ private LocalDateTime createdAt;
}
