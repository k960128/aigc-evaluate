package com.kant.llm.eval.dto.req;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 数据集样本分页查询请求参数。
 *
 * <p>用于接收分页查询数据集样本时前端提交的分页参数和数据集筛选条件。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataSetSamplePageRequest {

    /** 当前页码，默认值为 1。 */
    private Integer current;

    /** 每页数量，默认值为 10。 */
    private Integer size;

    /** 数据集 ID，用于筛选指定数据集下的样本。 */
    private Long datasetId;

    /** 题目绑定的风险小类 ID，用于筛选指定评测类型的样本。 */
    private Long riskDetailsId;
}
