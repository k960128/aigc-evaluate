package com.kant.llm.eval.agent.production.dao.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
/**
 * 人工选择正式用例精确版本形成的不可变集合，当前集合版本为1。
 * casesJson保留原选择顺序，不接受候选ID或latest；创建集合不调用被测目标。
 * 对应agent_sample_case_set表，不向外开放通用覆盖或删除接口；数据库时间约定为UTC微秒精度。
 */
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
    /** 有序CaseRef数组JSON，每项为caseId与精确version；不存候选ID或动态latest引用。 */ private String casesJson;
    /** UTC创建时间。 */ private LocalDateTime createdAt;
}
