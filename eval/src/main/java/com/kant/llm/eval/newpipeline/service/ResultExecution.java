package com.kant.llm.eval.newpipeline.service;

import com.kant.llm.common.enums.EvalResultStatusEnums;
import com.kant.llm.eval.task.dao.entity.EvalResultDetailDO;

import java.util.Set;

/** 模型调用、安全评测及人工重试共用执行锁和轮次口径。 */
public final class ResultExecution {
    private static final Set<Integer> TERMINAL = Set.of(
            EvalResultStatusEnums.FINISHED.getCode(), EvalResultStatusEnums.AUTO_SCORED.getCode(),
            EvalResultStatusEnums.MANUAL_REVIEWED.getCode(), EvalResultStatusEnums.FAILED.getCode(),
            EvalResultStatusEnums.STOPPED.getCode());

    private ResultExecution() {
    }

    public static String lockKey(Long resultDetailId) {
        return "EVAL:RESULT-EXECUTION:LOCK:" + resultDetailId;
    }

    /** 兼容上线前已投递的消息，缺省轮次为 0。 */
    public static int version(Integer version) {
        return version == null ? 0 : version;
    }

    public static boolean terminal(Integer status) {
        return status != null && TERMINAL.contains(status);
    }

    public static boolean current(EvalResultDetailDO result, Integer version) {
        return result != null && version(result.getExecutionVersion()) == version(version);
    }
}
