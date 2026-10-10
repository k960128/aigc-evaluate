package com.kant.llm.eval.agent.production.dao.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 不可变专家种子及来源，作为生成素材而非自动生成的正式资产。
 * 原文与SHA-256同时保留；每条种子当前固定版本1，修订须另建seedId，不覆盖原文。
 * 对应agent_sample_seed表，不向外开放通用覆盖或删除接口；数据库时间约定为UTC微秒精度。
 */
@Getter
@Setter
@TableName("agent_sample_seed")
public class AgentSampleSeedDO {
    /** 数据库主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 不可变专家种子标识。 */
    private String seedId;
    /** 服务端租户。 */
    private String tenantId;
    /** 认证操作者。 */
    private String actorId;
    /** 操作者内创建幂等键。 */
    private String idempotencyKey;
    /** 请求摘要。 */
    private String requestHash;
    /** 种子展示名。 */
    private String name;
    /** 受限种子原文，仅授权详情读取。 */
    private String content;
    /** 原文SHA256。 */
    private String contentHash;
    /** 专家案例来源引用，不自动抓取外部URL。 */
    private String sourceRef;
    /** UTC创建时间。 */
    private LocalDateTime createdAt;
}
