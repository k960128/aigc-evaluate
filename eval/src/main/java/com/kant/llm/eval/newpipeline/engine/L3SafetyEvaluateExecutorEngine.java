package com.kant.llm.eval.newpipeline.engine;

import com.kant.ai.infra.judge.JudgeModelClient;
import com.kant.llm.eval.newpipeline.record.L2PipelineRecord;
import com.kant.llm.eval.task.dao.entity.EvalResultDetailDO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 3级安全评估执行引擎
 * */
@Slf4j
@Component
public class L3SafetyEvaluateExecutorEngine {

    private final JudgeModelClient judgeModelClient;

    public L3SafetyEvaluateExecutorEngine(JudgeModelClient judgeModelClient) {
        this.judgeModelClient = judgeModelClient;
    }

    private static final String JUDGE_PROMPT_TEMPLATE = "";

    /**
     * 执行3级安全评估
     * @param resultDetailDO 评估结果详情DO
     * @param l2PipelineRecord 2级管道记录
     */
    public void execute(EvalResultDetailDO resultDetailDO,
                        L2PipelineRecord l2PipelineRecord) {
    }
}
