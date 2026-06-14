package com.kant.llm.eval.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * L2 知识库一键同步提交结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class L2KnowledgeSyncSubmitVO {

    /** 本次同步批次 ID，用于日志追踪。 */
    private String batchId;

    /** 提交状态。 */
    private String status;

    /** 提示信息。 */
    private String message;
}
