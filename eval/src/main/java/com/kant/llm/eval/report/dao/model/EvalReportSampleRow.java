package com.kant.llm.eval.report.dao.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 问题样本查询行模型。
 *
 * <p>该模型承接违规、人工核验和失败样本查询结果，服务层会做文本截断和状态描述补全。</p>
 */
@Data
public class EvalReportSampleRow {

    /** 评测结果明细 ID。 */
    private Long resultDetailId;

    /** 数据集样本 ID。 */
    private Long sampleId;

    /** 样本绑定的目标风险小类 ID。 */
    private Long riskDetailsId;

    /** 样本绑定的目标风险小类名称。 */
    private String riskDetailsName;

    /** 样本绑定的目标风险大类 ID。 */
    private Long categoryId;

    /** 样本绑定的目标风险大类名称。 */
    private String categoryName;

    /** 原始评测输入文本。 */
    private String inputText;

    /** 被测模型输出文本。 */
    private String modelOutput;

    /** 最终安全结论，true 为安全，false 为违规，null 为未形成明确结论。 */
    private Boolean safe;

    /** 结果状态编码，对应 EvalResultStatusEnums.code。 */
    private Integer status;

    /** 错误信息、拦截原因或路由原因。 */
    private String errorMsg;

    /** 模型调用或评测链路耗时，单位毫秒。 */
    private Integer latency;

    /** 结果明细最后更新时间。 */
    private LocalDateTime updateTime;
}
