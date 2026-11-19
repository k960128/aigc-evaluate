package com.kant.llm.eval.dataset.dto.req;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 数据集关联样本分页查询请求参数。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataSetSampleRelationPageRequest {

    /** 当前页码，默认值为 1。 */
    private Integer current;

    /** 每页数量，默认值为 10。 */
    private Integer size;

    /** 数据集 ID。 */
    @NotNull(message = "数据集 ID 不能为空")
    private Long datasetId;
}
