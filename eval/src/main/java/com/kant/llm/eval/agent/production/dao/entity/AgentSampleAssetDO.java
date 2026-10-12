package com.kant.llm.eval.agent.production.dao.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
/**
 * 人工批准形成的正式单消息资产；以caseId和caseVersion精确定位。
 * 正文通过candidateId引用并核对冻结哈希，不复制到旧dataset表；准入不代表目标风险已经验证。
 * 对应agent_sample_asset表，不向外开放通用覆盖或删除接口；数据库时间约定为UTC微秒精度。
 */
@Getter @Setter @TableName("agent_sample_asset")
public class AgentSampleAssetDO {
    /**
     * 正式资产是人工审核后的稳定输出，只引用候选正文并冻结其摘要和专家约束。
     * 它不是目标执行结果，也不等同于“已经发现漏洞”。
     */
    /** 数据库主键。 */ @TableId(type=IdType.AUTO) private Long id;
    /** 审核启用的正式单消息用例。 */ private String caseId;
    /** 不可变版本，首版1。 */ private Long caseVersion;
    /** 来源候选。 */ private String candidateId;
    /** 服务端租户。 */ private String tenantId;
    /** 操作者。 */ private String actorId;
    /** 审核记录引用。 */ private String approvalRef;
    /** Asset冻结定义：输入引用/哈希、专家前置条件、断言说明、风险/规则版本及审批引用，不含正文。 */ private String definitionJson;
    /** UTC发布时间。 */ private LocalDateTime createdAt;
}
