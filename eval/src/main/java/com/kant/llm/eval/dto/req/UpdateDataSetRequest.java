package com.kant.llm.eval.dto.req;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 更新数据集请求参数。
 *
 * <p>用于接收修改数据集时前端提交的数据集 ID 以及需要更新的字段。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateDataSetRequest {

    /** 数据集 ID，对应数据集信息表主键。 */
    private Long id;

    /** 数据集名称。 */
    private String datasetName;

    /** 数据集类型，用于标识评测数据集所属的业务类型。 */
    private Integer datasetType;

    /** 样本数量，表示当前数据集中包含的样本总数。 */
    private Integer sampleCount;

    /** 数据集描述，用于补充说明数据集用途、来源或适用场景。 */
    private String description;
}
