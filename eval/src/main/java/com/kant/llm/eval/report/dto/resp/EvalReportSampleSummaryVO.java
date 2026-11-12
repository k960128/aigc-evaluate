package com.kant.llm.eval.report.dto.resp;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 报告中的问题样本摘要。
 *
 * <p>报告默认不展开全部安全样本，只展示违规、人工核验和失败样本摘要。
 * 输入与输出字段会在服务层截断，完整内容仍通过结果明细分页接口查看。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvalReportSampleSummaryVO {

    /** 评测结果明细 ID，对应 eval_result_detail.id。 */
    private Long resultDetailId;

    /** 数据集样本 ID，对应 dataset_sample.id。 */
    private Long sampleId;

    /** 样本绑定的目标风险小类 ID。 */
    private Long riskDetailsId;

    /** 样本绑定的目标风险小类名称。 */
    private String riskDetailsName;

    /** 目标风险大类 ID。 */
    private Long categoryId;

    /** 目标风险大类名称。 */
    private String categoryName;

    /** 样本输入文本摘要，超过报告摘要长度会被截断。 */
    private String inputText;

    /** 模型输出文本摘要，超过报告摘要长度会被截断。 */
    private String modelOutput;

    /** 最终安全结论：0-未判定，1-安全，2-违规。 */
    private Integer isSafe;

    /** 结果明细状态编码，枚举参见 EvalResultStatusEnums。 */
    private Integer status;

    /** 结果明细状态中文描述。 */
    private String statusDesc;

    /** 错误信息、拦截原因或路由原因摘要。 */
    private String errorMsg;

    /** 模型调用或评测链路耗时，单位毫秒。 */
    private Integer latency;

    /** 结果明细最后更新时间。 */
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    private LocalDateTime updateTime;
}
