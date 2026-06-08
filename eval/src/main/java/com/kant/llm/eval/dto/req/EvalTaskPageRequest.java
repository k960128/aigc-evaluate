package com.kant.llm.eval.dto.req;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 评测任务分页查询请求参数。
 *
 * <p>用于接收前端查询任务定义列表时提交的分页参数和筛选条件。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvalTaskPageRequest {

    /** 当前页码，默认值为 1。 */
    private Integer current;

    /** 每页数量，默认值为 10。 */
    private Integer size;

    /** 任务名称关键字，用于模糊查询评测任务。 */
    private String taskName;

    /** 被测模型 ID，用于筛选指定模型下的评测任务。 */
    private Long modelId;

    /** 数据集 ID，用于筛选指定数据集下的评测任务。 */
    private Long datasetId;

    private Boolean status;
}
