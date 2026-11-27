package com.kant.llm.eval.report.dao.model;

import lombok.Data;

/**
 * 结果状态聚合查询行模型。
 *
 * <p>该模型只服务于 EvalReportMapper，不直接暴露给前端。</p>
 */
@Data
public class EvalReportStatusCountRow {

    /** 结果状态编码，对应 EvalResultStatusEnums.code。 */
    private Integer status;

    /** 安全结论，来自 eval_result_detail.is_safe：0-未判定，1-安全，2-违规。 */
    private Integer safe;

    /** 当前 status + safe 组合下的样本数量。 */
    private Long sampleCount;
}
