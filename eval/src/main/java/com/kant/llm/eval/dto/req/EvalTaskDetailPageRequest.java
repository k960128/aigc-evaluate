package com.kant.llm.eval.dto.req;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 评测任务执行批次分页查询请求参数。
 *
 * <p>用于按任务 ID 查询该任务历次提交产生的执行批次。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvalTaskDetailPageRequest {

    /** 当前页码，默认值为 1。 */
    private Integer current;

    /** 每页数量，默认值为 10。 */
    private Integer size;

    /** 评测任务 ID，必填，对应 eval_task.id。 */
    private Long taskId;

    /** 执行批次状态，用于筛选指定状态的批次。 */
    private Integer status;
}
