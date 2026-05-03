package com.kant.llm.eval.dto.req;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 创建评测任务请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateEvalTaskRequest {
    /** 任务名称 */
    private String taskName;
    /** 模型ID */
    private Long modelId;
    /** 数据集ID */
    private Long datasetId;
}
