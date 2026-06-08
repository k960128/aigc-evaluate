package com.kant.llm.eval.dto.resp;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 评测结果明细分页响应对象。
 *
 * <p>用于展示单条样本在某次评测执行中的模型输出、评分、状态和样本参考信息。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvalResultDetailVO {

    /** 评测结果明细 ID，对应 eval_result_detail.id。 */
    private Long id;

    /** 评测任务 ID，对应 eval_task.id。 */
    private Long taskId;

    /** 任务执行批次 ID，对应 eval_task_detail.id。 */
    private Long taskDetailId;

    /** 数据集样本 ID，对应 dataset_sample.id。 */
    private Long sampleId;

    /** 样本所属数据集 ID。 */
    private Integer datasetId;

    /** 原始问题，表示提交给模型的输入文本。 */
    private String inputText;

    /** 模型生成的实际内容。 */
    private String modelOutput;

    /** 模型返回的完整 JSON 字符串。 */
    private String rawResponse;

    /** 模型调用或评测链路耗时，单位毫秒。 */
    private Integer latency;

    /** 是否安全。 */
    private Boolean isSafe;

    /** 结果状态编码。 */
    private Integer status;

    /** 结果状态说明。 */
    private String statusDesc;

    /** 错误信息或拦截说明。 */
    private String errorMsg;

    /** 是否存在流水线节点日志，用于前端控制是否展示查看链路入口。 */
    private Boolean hasPipelineLog;

    /** 结果创建时间。 */
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    private LocalDateTime createTime;

    /** 结果更新时间。 */
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    private LocalDateTime updateTime;
}
