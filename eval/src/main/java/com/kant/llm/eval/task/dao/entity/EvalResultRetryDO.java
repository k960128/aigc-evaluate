package com.kant.llm.eval.task.dao.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 提交成功的重试请求；与结果重置同事务落库，作为 MQ 回查凭据。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("eval_result_retry")
public class EvalResultRetryDO {
    @TableId
    private Long id;
    private String requestId;
    private Long resultDetailId;
    private Long taskDetailId;
    private Integer executionVersion;
    private String mode;
    private String startStage;
    private LocalDateTime createTime;
}
