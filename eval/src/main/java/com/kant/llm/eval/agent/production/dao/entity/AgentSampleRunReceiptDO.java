package com.kant.llm.eval.agent.production.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/** 手动/补跑请求的首次回执；多个请求键可引用同一计划槽位，但不能更换各自请求内容。 */
@Getter
@Setter
@TableName("agent_sample_run_receipt")
public class AgentSampleRunReceiptDO {
    /** 数据库主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 所属生产计划，授权从计划继承。 */
    private String taskId;
    /** 计划内人工运行请求的幂等键，与自动调度键隔离。 */
    private String idempotencyKey;
    /** 原请求摘要，包括原槽位和受理时任务版本。 */
    private String requestHash;
    /** 首次请求引用的实际批次。 */
    private String runId;
    /** 首次受理快照；重试不因批次已推进而改变回执。 */
    private String resultJson;
}
