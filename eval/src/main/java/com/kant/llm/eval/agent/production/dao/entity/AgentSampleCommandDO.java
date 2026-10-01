package com.kant.llm.eval.agent.production.dao.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
/** D05agent_sample_command事实存储，不开放通用覆盖或删除接口。 */
@Getter @Setter @TableName("agent_sample_command")
public class AgentSampleCommandDO {
    /** 数据库主键。 */ @TableId(type=IdType.AUTO) private Long id;
    /** 任务或批次控制资源。 */ private String resourceId;
    /** 资源内命令幂等键。 */ private String idempotencyKey;
    /** 完整请求摘要。 */ private String requestHash;
    /** 首次命令回执。 */ private String resultJson;
}
