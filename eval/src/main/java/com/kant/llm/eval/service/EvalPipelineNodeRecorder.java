package com.kant.llm.eval.service;

import cn.hutool.core.util.IdUtil;
import com.alibaba.fastjson2.JSON;
import com.kant.llm.eval.common.enums.PipelineNodeCodeEnums;
import com.kant.llm.eval.common.enums.PipelineNodeStatusEnums;
import com.kant.llm.eval.dao.entity.EvalPipelineNodeDetailDO;
import com.kant.llm.eval.dao.entity.EvalResultDetailDO;
import com.kant.llm.eval.dao.entity.EvalTaskDetailDO;
import com.kant.llm.eval.dao.mapper.EvalPipelineNodeDetailMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 评测流水线节点记录组件。
 *
 * <p>该组件只负责记录节点执行历史，不参与节点业务判断。每次节点执行都会新建记录，
 * 便于后续排查重试、失败、短路和停止过程。</p>
 */
@Slf4j
@Component
public class EvalPipelineNodeRecorder {

    private final EvalPipelineNodeDetailMapper evalPipelineNodeDetailMapper;

    public EvalPipelineNodeRecorder(EvalPipelineNodeDetailMapper evalPipelineNodeDetailMapper) {
        this.evalPipelineNodeDetailMapper = evalPipelineNodeDetailMapper;
    }

    /**
     * 创建节点开始记录。
     *
     * @param taskDetail 任务执行批次
     * @param resultDetail 评测结果明细
     * @param nodeCode 节点编码
     * @param inputSnapshot 节点输入快照对象，会被序列化为 JSON
     * @return 节点记录 ID
     */
    public Long startNode(EvalTaskDetailDO taskDetail,
                          EvalResultDetailDO resultDetail,
                          PipelineNodeCodeEnums nodeCode,
                          Object inputSnapshot) {
        Long nodeRecordId = IdUtil.getSnowflake().nextId();
        EvalPipelineNodeDetailDO nodeDetail = EvalPipelineNodeDetailDO.builder()
                .id(nodeRecordId)
                .taskId(taskDetail.getTaskId())
                .taskDetailId(taskDetail.getId())
                .resultDetailId(resultDetail.getId())
                .sampleId(resultDetail.getSampleId())
                .nodeCode(nodeCode.getCode())
                .status(PipelineNodeStatusEnums.RUNNING.getCode())
                .startTime(LocalDateTime.now())
                .inputSnapshot(toJson(inputSnapshot))
                .build();
        evalPipelineNodeDetailMapper.insert(nodeDetail);
        log.info("流水线节点开始记录已写入，nodeRecordId: {}, nodeCode: {}, taskDetailId: {}, resultDetailId: {}",
                nodeRecordId, nodeCode.getCode(), taskDetail.getId(), resultDetail.getId());
        return nodeRecordId;
    }

    /**
     * 标记节点正常完成。
     */
    public void finishNode(Long nodeRecordId,
                           PipelineNodeStatusEnums status,
                           Object outputSnapshot,
                           Object nodeResult,
                           String errorMsg) {
        finishNodeInternal(nodeRecordId, status, outputSnapshot, nodeResult, errorMsg);
    }

    /**
     * 标记节点执行失败。
     */
    public void failNode(Long nodeRecordId, Exception ex, Object outputSnapshot, Object nodeResult) {
        finishNodeInternal(nodeRecordId, PipelineNodeStatusEnums.FAILED, outputSnapshot, nodeResult, trimErrorMessage(ex.getMessage()));
    }

    /**
     * 标记节点被跳过。
     */
    public void skipNode(Long nodeRecordId, String reason, Object nodeResult) {
        finishNodeInternal(nodeRecordId, PipelineNodeStatusEnums.SKIPPED, null, nodeResult, reason);
    }

    /**
     * 标记节点因用户停止批次而终止。
     */
    public void stopNode(Long nodeRecordId, String reason, Object nodeResult) {
        finishNodeInternal(nodeRecordId, PipelineNodeStatusEnums.STOPPED, null, nodeResult, reason);
    }

    /**
     * 完成节点记录更新，自动计算节点耗时。
     */
    private void finishNodeInternal(Long nodeRecordId,
                                    PipelineNodeStatusEnums status,
                                    Object outputSnapshot,
                                    Object nodeResult,
                                    String errorMsg) {
        if (nodeRecordId == null) {
            return;
        }
        EvalPipelineNodeDetailDO current = evalPipelineNodeDetailMapper.selectById(nodeRecordId);
        if (current == null) {
            log.warn("流水线节点记录不存在，跳过更新，nodeRecordId: {}", nodeRecordId);
            return;
        }
        LocalDateTime endTime = LocalDateTime.now();
        EvalPipelineNodeDetailDO updateEntity = EvalPipelineNodeDetailDO.builder()
                .id(nodeRecordId)
                .status(status.getCode())
                .endTime(endTime)
                .latency(calculateLatency(current.getStartTime(), endTime))
                .outputSnapshot(toJson(outputSnapshot))
                .nodeResult(toJson(nodeResult))
                .errorMsg(trimErrorMessage(errorMsg))
                .build();
        evalPipelineNodeDetailMapper.updateById(updateEntity);
        log.info("流水线节点记录已更新，nodeRecordId: {}, nodeCode: {}, status: {}, latency: {}",
                nodeRecordId, current.getNodeCode(), status.getCode(), updateEntity.getLatency());
    }

    /**
     * 将对象转换成 JSON 字符串，空对象保持为空。
     */
    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        return JSON.toJSONString(value);
    }

    /**
     * 计算节点耗时，超出 Integer 范围时取最大值。
     */
    private Integer calculateLatency(LocalDateTime startTime, LocalDateTime endTime) {
        if (startTime == null || endTime == null) {
            return null;
        }
        long millis = Duration.between(startTime, endTime).toMillis();
        return millis > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) millis;
    }

    /**
     * 错误信息落库时做长度保护。
     */
    private String trimErrorMessage(String message) {
        if (message == null) {
            return null;
        }
        int maxLength = 1000;
        return message.length() <= maxLength ? message : message.substring(0, maxLength);
    }
}
