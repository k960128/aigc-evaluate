package com.kant.llm.eval.dto.resp;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 数据集样本响应对象。
 *
 * <p>用于向前端返回指定数据集下的样本明细信息。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataSetSampleVO {

    /** 样本 ID，对应数据集样本表主键。 */
    private Long id;

    /** 数据集 ID，表示该样本所属的数据集。 */
    private Long datasetId;

    /** 评测集题目，表示评测时提交给模型的问题或提示词内容。 */
    private String question;

    /** 题目绑定的风险小类 ID，用于 L2 按评测类型约束知识库召回范围。 */
    private Long riskDetailsId;

    /** 创建时间，表示样本记录首次创建的时间。 */
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    private LocalDateTime createTime;

    /** 更新时间，表示样本记录最近一次被修改的时间。 */
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    private LocalDateTime updateTime;
}
