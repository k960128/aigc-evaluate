package com.kant.llm.eval.newpipeline.service;

import cn.hutool.core.collection.CollectionUtil;
import com.kant.framework.exception.ServiceException;
import com.kant.llm.common.convention.EvalContext;
import com.kant.llm.common.enums.EvalResultStatusEnums;
import com.kant.llm.common.enums.PipelineNodeCodeEnums;
import com.kant.llm.common.enums.PipelineNodeStatusEnums;
import com.kant.llm.common.enums.TaskStatusEnums;
import com.kant.llm.eval.knowledge.engine.L1InterceptionEngine;
import com.kant.llm.eval.pipeline.mq.message.SafetyEvaluateMessage;
import com.kant.llm.eval.pipeline.service.EvalPipelineNodeRecorder;
import com.kant.llm.eval.pipeline.service.l2.L2EvaluationService;
import com.kant.llm.eval.pipeline.service.l2.model.L2EvaluationRequest;
import com.kant.llm.eval.pipeline.service.l2.model.L2EvaluationResult;
import com.kant.llm.eval.task.dao.entity.EvalResultDetailDO;
import com.kant.llm.eval.task.dao.entity.EvalTaskDetailDO;
import com.kant.llm.eval.task.service.EvalResultDetailService;
import com.kant.llm.eval.task.service.EvalTaskDetailService;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 安全评测执行服务流水线
 */
@Slf4j
@Service
public class SafetyEvaluateExecuteService {
    private static final String EXECUTION_LOCK_KEY_PREFIX = "EVAL:SAFETY-EVALUATE:LOCK:KEY:%s";


    private final RedissonClient redissonClient;
    private final L2EvaluationService l2EvaluationService;
    private final L1InterceptionEngine l1InterceptionEngine;
    private final EvalTaskDetailService evalTaskDetailService;
    private final EvalResultDetailService evalResultDetailService;
    private final EvalPipelineNodeRecorder evalPipelineNodeRecorder;

    public SafetyEvaluateExecuteService(RedissonClient redissonClient,
                                        L2EvaluationService l2EvaluationService,
                                        L1InterceptionEngine l1InterceptionEngine,
                                        EvalTaskDetailService evalTaskDetailService,
                                        EvalResultDetailService evalResultDetailService,
                                        EvalPipelineNodeRecorder evalPipelineNodeRecorder) {
        this.redissonClient = redissonClient;
        this.l2EvaluationService = l2EvaluationService;
        this.l1InterceptionEngine = l1InterceptionEngine;
        this.evalTaskDetailService = evalTaskDetailService;
        this.evalResultDetailService = evalResultDetailService;
        this.evalPipelineNodeRecorder = evalPipelineNodeRecorder;
    }

    /**
     * 提交安全评测执行任务
     * @param message msg
     */
    public void submitExecute(SafetyEvaluateMessage message) {
        // 获取分布式锁
        String lockKey = buildLockKey(message.getResultDetailId());
        RLock rLock = redissonClient.getLock(lockKey);
        try {
            // 尝试获取锁：等待10秒，持有30秒后自动释放
            boolean locked = rLock.tryLock(10, 30, TimeUnit.SECONDS);
            if (!locked) {
                log.info("评测结果明细正在执行中，跳过重复模型调用消息，resultDetailId: {}",
                        message.getResultDetailId());
                return;
            }
            try {
                // 执行业务逻辑
                execute(message);


                // ====== end 完整评测链路，回写结果
                Long resultDetailId = message.getResultDetailId();
                EvalResultDetailDO resultDetailDO = evalResultDetailService.getById(resultDetailId);
                writeBackToDB(resultDetailDO);
            } catch (Exception e) {
                log.error("业务执行异常: {}", lockKey, e);
                // 根据业务需要决定是否抛出或记录
                throw new ServiceException(String.format("处理失败，resultDetailId: %s", e.getMessage()));
            } finally {
                // 只有当前线程持有锁时才释放
                if (rLock.isHeldByCurrentThread()) {
                    rLock.unlock();
                    log.debug("释放锁成功: {}", lockKey);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("获取锁被中断: {}", lockKey, e);
            throw new RuntimeException("获取锁被中断", e);
        }
    }

    /**
     * 执行安全评测业务逻辑
     * @param message msg
     */
    private void execute(SafetyEvaluateMessage message) {
        Long resultDetailId = message.getResultDetailId();
        EvalResultDetailDO resultDetailDO = evalResultDetailService.getById(resultDetailId);
        // ================= L1 关键词拦截
        Boolean l1Safetied = safetyEvaluateL1(resultDetailDO);
        // L1 短路点，命中规则直接返回
        if (l1Safetied) {
            return;
        }
        // ================= L2 混合检索
        Boolean l2Safetied = safetyEvaluateL2(resultDetailDO);
        // L2 短路点，混合检索命中规则直接返回
        if (l2Safetied) {
            return;
        }
        // ================= L3 投递到LLM-AS-JUDGE
        safetyEvaluateL3(resultDetailDO);
    }

    /**
     * 执行 L1 评测
     * @param resultDetailDO 评测结果明细
     * @return 是否命中
     */
    private Boolean safetyEvaluateL1(EvalResultDetailDO resultDetailDO) {
        // 1. 记录节点日志
        Long l1NodeRecordId = evalPipelineNodeRecorder.startNode(
                resultDetailDO.getTaskId(),
                resultDetailDO.getTaskDetailId(),
                resultDetailDO,
                PipelineNodeCodeEnums.L1,
                CollectionUtil.toList(resultDetailDO.getModelOutput()));
        // 2. 获取 L1 评测上下文
        EvalContext context = l1InterceptionEngine.analyze(resultDetailDO.getModelOutput());
        // 3. 判断是否命中拦截
        if (context.isL1Blocked()) {
            log.info("L1 判定命中拦截，resultDetailId: {}, riskDetailsId: {}, keyword: {}",
                    resultDetailDO.getId(), context.getL1BlockDetailsId(), context.getL1BlockKeyword());

            // ========== 回写结果明细
            resultDetailDO.setIsSafe(Boolean.FALSE);
            resultDetailDO.setErrorMsg(String.format("L1 判定命中拦截，风险明细ID: %s，触发关键词: %s",
                    context.getL1BlockDetailsId(), context.getL1BlockKeyword()));
            evalResultDetailService.updateById(resultDetailDO);
            // ========== 记录L1节点明细
            evalPipelineNodeRecorder.finishNode(l1NodeRecordId,
                    PipelineNodeStatusEnums.BLOCKED,
                    new LinkedHashMap<String, Object>() {{
                        put("l1Blocked", context.isL1Blocked());
                        put("l1BlockDetailsId", context.getL1BlockDetailsId());
                        put("l1BlockKeyword", context.getL1BlockKeyword());
                        put("hitWarningTags", context.getHitWarningTags());
                    }},
                    new LinkedHashMap<String, Object>() {{
                        put("blocked", context.isL1Blocked());
                        put("riskDetailsId", context.getL1BlockDetailsId());
                        put("keyword", context.getL1BlockKeyword());
                        put("warningTags", context.getHitWarningTags());
                        put("decision", PipelineNodeStatusEnums.BLOCKED.getDesc());
                    }}, resultDetailDO.getErrorMsg());
            return true;
        }
        // L1 安全判定通过，记录放行节点
        evalPipelineNodeRecorder.finishNode(l1NodeRecordId, PipelineNodeStatusEnums.PASSED,
                new LinkedHashMap<String, Object>() {{
                    put("l1Blocked", context.isL1Blocked());
                    put("l1BlockDetailsId", context.getL1BlockDetailsId());
                    put("l1BlockKeyword", context.getL1BlockKeyword());
                    put("hitWarningTags", context.getHitWarningTags());
                }},
                new LinkedHashMap<String, Object>() {{
                    put("blocked", context.isL1Blocked());
                    put("riskDetailsId", context.getL1BlockDetailsId());
                    put("keyword", context.getL1BlockKeyword());
                    put("warningTags", context.getHitWarningTags());
                    put("decision", PipelineNodeStatusEnums.PASSED.getDesc());
                }}, null);
        log.info("L1 判定通过，resultDetailId: {}, warningTags: {}", resultDetailDO.getId(), context.getHitWarningTags());
        return false;
    }

    /**
     * 执行 L2 评测
     * @param resultDetailDO 评测结果明细
     */
    private Boolean safetyEvaluateL2(EvalResultDetailDO resultDetailDO) {
        // 1. 记录L2 节点起始日志
        Long l2NodeRecordId = evalPipelineNodeRecorder.startNode(
                resultDetailDO.getTaskId(),
                resultDetailDO.getTaskDetailId(),
                resultDetailDO,
                PipelineNodeCodeEnums.L2,
                new LinkedHashMap<String, Object>() {{
                    put("inputText", resultDetailDO.getInputText());
                    put("targetRiskDetailsId", resultDetailDO.getRiskDetailsId());
                    put("modelOutput", resultDetailDO.getModelOutput());
                }}
        );

        // 2. 执行 L2 评测 并获取评测结果
        L2EvaluationResult evaluateL2Result = l2EvaluationService.evaluate(L2EvaluationRequest.builder()
                .taskId(resultDetailDO.getTaskId())
                .taskDetailId(resultDetailDO.getTaskDetailId())
                .resultDetailId(resultDetailDO.getId())
                .sampleId(resultDetailDO.getSampleId())
                .targetRiskDetailsId(resultDetailDO.getRiskDetailsId())
                .inputText(resultDetailDO.getInputText())
                .modelOutput(resultDetailDO.getModelOutput())
                .build());

        // 判定是否违规
        if (!evaluateL2Result.getSafe()) {
            // ========== 回写结果明细状态
            resultDetailDO.setIsSafe(Boolean.FALSE);
            resultDetailDO.setErrorMsg(String.format("L2 安全判定：%s， riskDetailsId=%s， reason=%s",
                    evaluateL2Result.getDecisionType().getDesc(),
                    evaluateL2Result.getRiskDetailsId(),
                    evaluateL2Result.getRouteReason()));
            resultDetailDO.setStatus(EvalResultStatusEnums.AUTO_SCORED.getCode());
            evalResultDetailService.updateById(resultDetailDO);

            // ========== 记录 L2 节点违规日志
            evalPipelineNodeRecorder.finishNode(l2NodeRecordId,
                    PipelineNodeStatusEnums.BLOCKED,
                    evaluateL2Result.getOutputSnapshot(),
                    evaluateL2Result.getNodeResult(),
                    resultDetailDO.getErrorMsg());
            return true;
        }
        // L2 判定放行 记录放行节点日志
        evalPipelineNodeRecorder.finishNode(l2NodeRecordId,
                PipelineNodeStatusEnums.PASSED,
                evaluateL2Result.getOutputSnapshot(),
                evaluateL2Result.getNodeResult(),
                null);
        return false;
    }

    /**
     * 执行 L3 评测
     * @param resultDetailDO 评测结果明细
     */
    private void safetyEvaluateL3(EvalResultDetailDO resultDetailDO) {
        log.info("L3 评测，resultDetailId: {}", resultDetailDO.getId());
//        return true;
    }

    /**
     * 构建分布式锁的Key
     * @param resultDetailId 回写结果ID
     * @return Key
     */
    private String buildLockKey(Long resultDetailId) {
        return String.format(EXECUTION_LOCK_KEY_PREFIX, resultDetailId);
    }


    /**
     * 评测完成回写结果
     * @param resultDetailDO do
     */
    private void writeBackToDB(EvalResultDetailDO resultDetailDO) {
        resultDetailDO.setStatus(EvalResultStatusEnums.FINISHED.getCode());
        evalResultDetailService.updateById(resultDetailDO);

        // 模拟整体任务完成，后续要做修改
        EvalTaskDetailDO evalTaskDetailDO = evalTaskDetailService.getById(resultDetailDO.getTaskDetailId());
        evalTaskDetailDO.setStatus(TaskStatusEnums.COMPLETED.getCode());
        evalTaskDetailDO.setFinishedCount(evalTaskDetailDO.getTotalCount());
        evalTaskDetailDO.setEndTime(LocalDateTime.now());
        evalTaskDetailService.updateById(evalTaskDetailDO);
    }
}
