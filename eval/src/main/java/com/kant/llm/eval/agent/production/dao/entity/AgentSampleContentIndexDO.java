package com.kant.llm.eval.agent.production.dao.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
/** D05agent_sample_content_index事实存储，不开放通用覆盖或删除接口。 */
@Getter @Setter @TableName("agent_sample_content_index")
public class AgentSampleContentIndexDO {
    /** 数据库主键。 */ @TableId(type=IdType.AUTO) private Long id;
    /** 服务端租户。 */ private String tenantId;
    /** 操作者。 */ private String actorId;
    /** 去重规则版本。 */ private String dedupVersion;
    /** 规范正文摘要。 */ private String normalizedHash;
    /** 首个质量通过候选，不覆盖原正文。 */ private String candidateId;
}
