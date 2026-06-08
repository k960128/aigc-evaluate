package com.kant.llm.eval.dto.req;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 评测结果明细分页查询请求参数。
 *
 * <p>用于查询某个任务或某次执行批次下的单样本评测结果列表。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvalResultDetailPageRequest {

    /** 当前页码，默认值为 1。 */
    private Integer current;

    /** 每页数量，默认值为 10。 */
    private Integer size;

    /** 评测任务 ID，可用于查询某个任务下的全部结果。 */
    private Long taskId;

    /** 任务执行批次 ID，推荐传入，用于精确查询某次执行批次下的结果。 */
    private Long taskDetailId;

    /** 数据集样本 ID，用于定位某条样本的评测结果。 */
    private Long sampleId;

    /** 原始问题关键字，用于按输入文本模糊查询。 */
    private String keyword;

    /** 结果状态：0-未处理, 1-已自动评分, 2-已人工核验, 3-执行失败, 4-已终止。 */
    private Integer status;

    /** 是否安全，用于筛选安全或不安全的样本结果。 */
    private Boolean isSafe;
}
