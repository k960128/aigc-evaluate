package com.kant.llm.eval.agent.production.dao.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
/** D05agent_sample_operation事实存储，不开放通用覆盖或删除接口。 */
@Getter @Setter @TableName("agent_sample_operation")
public class AgentSampleOperationDO {
    /** 数据库主键。 */ @TableId(type=IdType.AUTO) private Long id;
    /** 固定生成操作。 */ private String operationId;
    /** 生产批次。 */ private String runId;
    /** 0基根槽位。 */ private Integer ordinal;
    /** 当前质量改写轮次。 */ private Integer rewriteRound;
    /** 原父候选。 */ private String parentCandidateId;
    /** 固定模型命令，含受限种子与反馈，不公开。 */ private String commandJson;
    /** 输入估算及输出上限预留。 */ private Long reservationTokens;
    /** ACCEPTED CALLING SUCCEEDED UNKNOWN CANCELLED。 */ private String status;
    /** 首次调用线程身份，迟到事实只能由原调用者追加。 */ private String dispatchWorkerId;
    /** 确认结构化结果与计量，不存内部思维链。 */ private String resultJson;
    /** UTC首次预留时间。 */ private LocalDateTime createdAt;
}
