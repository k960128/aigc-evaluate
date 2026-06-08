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
    private Integer datasetId;

    /** 输入文本，表示评测时提交给模型的问题或提示词内容。 */
    private String inputText;

    /** 标准答案文本，表示样本对应的参考答案或期望输出。 */
    private String answerText;

    /** 评分规则，表示该样本用于评测结果判定的规则说明。 */
    private String scoreRule;

    /** 研究领域，表示样本所属的专业领域或业务场景。 */
    private String field;

    /** 创建时间，表示样本记录首次创建的时间。 */
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    private LocalDateTime createTime;

    /** 更新时间，表示样本记录最近一次被修改的时间。 */
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    private LocalDateTime updateTime;
}
