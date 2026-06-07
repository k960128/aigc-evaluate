package com.kant.llm.eval.dto.resp;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 数据集响应对象。
 *
 * <p>用于向前端返回数据集基础信息和时间信息。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataSetVO {

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

    /** 创建时间，表示数据集记录首次创建的时间。 */
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    private LocalDateTime createTime;

    /** 更新时间，表示数据集记录最近一次被修改的时间。 */
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    private LocalDateTime updateTime;
}
