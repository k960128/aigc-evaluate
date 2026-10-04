package com.kant.llm.eval.agent.production.dao.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
/**
 * 质量PASS候选的跨批次规范化精确去重索引，不提供语义相似判断。
 * 按租户、操作者、去重版本和规范化摘要隔离；首候选退回后也不自动释放索引。
 * 对应agent_sample_content_index表，不向外开放通用覆盖或删除接口；数据库时间约定为UTC微秒精度。
 */
@Getter @Setter @TableName("agent_sample_content_index")
public class AgentSampleContentIndexDO {
    /** 数据库主键。 */ @TableId(type=IdType.AUTO) private Long id;
    /** 服务端租户。 */ private String tenantId;
    /** 操作者。 */ private String actorId;
    /** 去重规则版本。 */ private String dedupVersion;
    /** 规范正文摘要。 */ private String normalizedHash;
    /** 首个质量通过候选，不覆盖原正文。 */ private String candidateId;
}
