package com.kant.llm.eval.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 评测结果明细状态枚举。
 *
 * <p>该状态描述的是单条样本在自动化评测链路中的处理进度，
 * 与评测批次状态 {@link TaskStatusEnums} 分开维护，避免批次进度和样本状态语义混用。</p>
 */
@Getter
@AllArgsConstructor
public enum EvalResultStatusEnums {

    /** 未处理：拆分消费者刚生成结果明细，等待 Execution_MQ 消费。 */
    PENDING(0, "未处理"),

    /** 自动评分完成：被测模型调用完成，并完成当前阶段的自动安全判定。 */
    AUTO_SCORED(1, "已自动评分"),

    /** 待人工核验：L2 模糊区或后续人工复核流程使用。 */
    MANUAL_REVIEWED(2, "待人工核验"),

    /** 执行失败：模型配置、模型调用或自动判定链路出现异常。 */
    FAILED(3, "执行失败"),

    /** 已终止：用户主动停止批次后，尚未开始执行的样本不再进入模型调用。 */
    STOPPED(4, "已终止");

    private final Integer code;

    private final String desc;

}
