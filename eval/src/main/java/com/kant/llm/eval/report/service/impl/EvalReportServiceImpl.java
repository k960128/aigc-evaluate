package com.kant.llm.eval.report.service.impl;

import com.kant.framework.exception.ServiceException;
import com.kant.llm.common.enums.EvalResultStatusEnums;
import com.kant.llm.common.enums.EvalSafeStatusEnums;
import com.kant.llm.common.enums.PipelineNodeCodeEnums;
import com.kant.llm.common.enums.PipelineNodeStatusEnums;
import com.kant.llm.common.enums.TaskStatusEnums;
import com.kant.llm.eval.basesource.dao.entity.ModelInfoDO;
import com.kant.llm.eval.basesource.dao.mapper.ModelInfoMapper;
import com.kant.llm.eval.dataset.dao.entity.DataSetDO;
import com.kant.llm.eval.dataset.dao.mapper.DataSetMapper;
import com.kant.llm.eval.report.dao.mapper.EvalReportMapper;
import com.kant.llm.eval.report.dao.model.EvalReportPipelineStatRow;
import com.kant.llm.eval.report.dao.model.EvalReportRiskStatRow;
import com.kant.llm.eval.report.dao.model.EvalReportSampleRow;
import com.kant.llm.eval.report.dao.model.EvalReportStatusCountRow;
import com.kant.llm.eval.report.dto.resp.EvalReportExecutionSummaryVO;
import com.kant.llm.eval.report.dto.resp.EvalReportMetaVO;
import com.kant.llm.eval.report.dto.resp.EvalReportPipelineBreakdownVO;
import com.kant.llm.eval.report.dto.resp.EvalReportRiskBreakdownVO;
import com.kant.llm.eval.report.dto.resp.EvalReportSafetySummaryVO;
import com.kant.llm.eval.report.dto.resp.EvalReportSampleSummaryVO;
import com.kant.llm.eval.report.dto.resp.EvalTaskReportVO;
import com.kant.llm.eval.report.service.EvalReportMetricCalculator;
import com.kant.llm.eval.report.service.EvalReportService;
import com.kant.llm.eval.task.dao.entity.EvalTaskDO;
import com.kant.llm.eval.task.dao.entity.EvalTaskDetailDO;
import com.kant.llm.eval.task.dao.mapper.EvalTaskDetailMapper;
import com.kant.llm.eval.task.dao.mapper.EvalTaskMapper;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 评测报告服务实现。
 *
 * <p>该实现只读取已有任务、结果明细和流水线节点日志，不创建报告记录，不发送 MQ。
 * 报告可以在批次运行中生成，此时 meta.finalized=false，表示当前数据快照。</p>
 */
@Service
public class EvalReportServiceImpl implements EvalReportService {

    /** 样本文本摘要最大长度，防止报告响应中携带过长 prompt 或模型输出。 */
    private static final int TEXT_DIGEST_LENGTH = 200;

    private final EvalTaskDetailMapper evalTaskDetailMapper;
    private final EvalTaskMapper evalTaskMapper;
    private final ModelInfoMapper modelInfoMapper;
    private final DataSetMapper dataSetMapper;
    private final EvalReportMapper evalReportMapper;

    public EvalReportServiceImpl(EvalTaskDetailMapper evalTaskDetailMapper,
                                 EvalTaskMapper evalTaskMapper,
                                 ModelInfoMapper modelInfoMapper,
                                 DataSetMapper dataSetMapper,
                                 EvalReportMapper evalReportMapper) {
        this.evalTaskDetailMapper = evalTaskDetailMapper;
        this.evalTaskMapper = evalTaskMapper;
        this.modelInfoMapper = modelInfoMapper;
        this.dataSetMapper = dataSetMapper;
        this.evalReportMapper = evalReportMapper;
    }

    @Override
    public EvalTaskReportVO getTaskReport(Long taskDetailId, Integer topN) {
        if (taskDetailId == null) {
            throw new ServiceException("评测任务执行批次ID不能为空");
        }
        EvalTaskDetailDO taskDetail = evalTaskDetailMapper.selectById(taskDetailId);
        if (taskDetail == null) {
            throw new ServiceException("评测任务执行批次不存在，无法生成报告");
        }

        // topN 只影响问题样本列表，不影响全量统计查询；这样报告既轻量，又保留完整统计口径。
        Integer sampleLimit = EvalReportMetricCalculator.sanitizeTopN(topN);
        EvalTaskDO task = taskDetail.getTaskId() == null ? null : evalTaskMapper.selectById(taskDetail.getTaskId());
        ModelInfoDO modelInfo = taskDetail.getModelId() == null ? null : modelInfoMapper.selectById(taskDetail.getModelId());
        DataSetDO dataSet = taskDetail.getDatasetId() == null ? null : dataSetMapper.selectById(taskDetail.getDatasetId());
        List<EvalReportStatusCountRow> statusRows = safeList(evalReportMapper.countResultStatus(taskDetailId));

        return EvalTaskReportVO.builder()
                .meta(buildMeta(taskDetail, task, modelInfo, dataSet))
                .execution(buildExecutionSummary(taskDetail, statusRows))
                .safety(buildSafetySummary(statusRows))
                .riskBreakdown(buildRiskBreakdown(evalReportMapper.listRiskBreakdown(taskDetailId)))
                .pipelineBreakdown(buildPipelineBreakdown(evalReportMapper.listPipelineBreakdown(taskDetailId)))
                .unsafeSamples(buildSampleSummaries(evalReportMapper.listUnsafeSamples(taskDetailId, sampleLimit)))
                .manualReviewSamples(buildSampleSummaries(evalReportMapper.listManualReviewSamples(taskDetailId, sampleLimit)))
                .failedSamples(buildSampleSummaries(evalReportMapper.listFailedSamples(taskDetailId, sampleLimit)))
                .build();
    }

    private EvalReportMetaVO buildMeta(EvalTaskDetailDO taskDetail,
                                       EvalTaskDO task,
                                       ModelInfoDO modelInfo,
                                       DataSetDO dataSet) {
        LocalDateTime generatedAt = LocalDateTime.now();
        return EvalReportMetaVO.builder()
                .taskId(taskDetail.getTaskId())
                .taskDetailId(taskDetail.getId())
                .serialNo(taskDetail.getSerialNo())
                .taskName(taskDetail.getTaskName() == null && task != null ? task.getTaskName() : taskDetail.getTaskName())
                .modelId(taskDetail.getModelId())
                .modelName(modelInfo == null ? null : modelInfo.getModel())
                .datasetId(taskDetail.getDatasetId())
                .datasetName(dataSet == null ? null : dataSet.getDatasetName())
                .status(taskDetail.getStatus())
                .statusDesc(taskStatusDesc(taskDetail.getStatus()))
                .finalized(isFinalized(taskDetail.getStatus()))
                .startTime(taskDetail.getStartTime())
                .endTime(taskDetail.getEndTime())
                .durationMillis(calculateDurationMillis(taskDetail.getStartTime(), taskDetail.getEndTime(), generatedAt))
                .generatedAt(generatedAt)
                .build();
    }

    private EvalReportExecutionSummaryVO buildExecutionSummary(EvalTaskDetailDO taskDetail,
                                                              List<EvalReportStatusCountRow> statusRows) {
        Map<Integer, Long> statusCounts = buildStatusCounts(statusRows);
        long pendingCount = getStatusCount(statusCounts, EvalResultStatusEnums.PENDING);
        long modelRunningCount = getStatusCount(statusCounts, EvalResultStatusEnums.MODEL_RUNNING);
        long modelCalledCount = getStatusCount(statusCounts, EvalResultStatusEnums.MODEL_CALLED);
        long safetyEvaluatingCount = getStatusCount(statusCounts, EvalResultStatusEnums.SAFETY_EVALUATING);
        long autoScoredCount = getStatusCount(statusCounts, EvalResultStatusEnums.AUTO_SCORED);
        long manualReviewCount = getStatusCount(statusCounts, EvalResultStatusEnums.MANUAL_REVIEWED);
        long failedCount = getStatusCount(statusCounts, EvalResultStatusEnums.FAILED);
        long stoppedCount = getStatusCount(statusCounts, EvalResultStatusEnums.STOPPED);
        long resultCount = statusCounts.values().stream().filter(Objects::nonNull).mapToLong(Long::longValue).sum();
        int totalCount = resolveTotalCount(taskDetail, resultCount);
        int finishedCount = taskDetail.getFinishedCount() == null ? 0 : taskDetail.getFinishedCount();

        // 执行成功率按总样本数计算，表达“本批次有多少样本成功走到自动评分或人工核验阶段”。
        return EvalReportExecutionSummaryVO.builder()
                .totalCount(totalCount)
                .finishedCount(finishedCount)
                .resultCount(resultCount)
                .pendingCount(pendingCount)
                .modelRunningCount(modelRunningCount)
                .modelCalledCount(modelCalledCount)
                .safetyEvaluatingCount(safetyEvaluatingCount)
                .autoScoredCount(autoScoredCount)
                .manualReviewCount(manualReviewCount)
                .failedCount(failedCount)
                .stoppedCount(stoppedCount)
                .progressPercent(EvalReportMetricCalculator.percent(finishedCount, totalCount))
                .executionSuccessRate(EvalReportMetricCalculator.rate(autoScoredCount + manualReviewCount, totalCount))
                .failedRate(EvalReportMetricCalculator.rate(failedCount, totalCount))
                .stoppedRate(EvalReportMetricCalculator.rate(stoppedCount, totalCount))
                .statusCounts(statusCounts)
                .build();
    }

    private EvalReportSafetySummaryVO buildSafetySummary(List<EvalReportStatusCountRow> statusRows) {
        long autoScoredCount = 0;
        long safeCount = 0;
        long unsafeCount = 0;
        long manualReviewCount = 0;
        for (EvalReportStatusCountRow row : statusRows) {
            long count = nvl(row.getSampleCount());
            if (EvalResultStatusEnums.AUTO_SCORED.getCode().equals(row.getStatus())) {
                autoScoredCount += count;
                if (EvalSafeStatusEnums.SAFE.getCode().equals(row.getSafe())) {
                    safeCount += count;
                } else if (EvalSafeStatusEnums.VIOLATION.getCode().equals(row.getSafe())) {
                    unsafeCount += count;
                }
            } else if (EvalResultStatusEnums.MANUAL_REVIEWED.getCode().equals(row.getStatus())) {
                manualReviewCount += count;
            }
        }
        // 安全率和风险率只使用已经形成明确安全/违规结论的样本作为分母，人工核验样本单列。
        long conclusiveCount = safeCount + unsafeCount;
        long evaluatedCount = conclusiveCount + manualReviewCount;
        return EvalReportSafetySummaryVO.builder()
                .autoScoredCount(autoScoredCount)
                .safeCount(safeCount)
                .unsafeCount(unsafeCount)
                .manualReviewCount(manualReviewCount)
                .conclusiveCount(conclusiveCount)
                .evaluatedCount(evaluatedCount)
                .safeRate(EvalReportMetricCalculator.rate(safeCount, conclusiveCount))
                .riskRate(EvalReportMetricCalculator.rate(unsafeCount, conclusiveCount))
                .manualReviewRate(EvalReportMetricCalculator.rate(manualReviewCount, evaluatedCount))
                .build();
    }

    private List<EvalReportRiskBreakdownVO> buildRiskBreakdown(List<EvalReportRiskStatRow> rows) {
        return safeList(rows).stream()
                .map(row -> {
                    long totalCount = nvl(row.getTotalCount());
                    long safeCount = nvl(row.getSafeCount());
                    long unsafeCount = nvl(row.getUnsafeCount());
                    long manualReviewCount = nvl(row.getManualReviewCount());
                    long conclusiveCount = safeCount + unsafeCount;
                    long evaluatedCount = conclusiveCount + manualReviewCount;
                    // 风险维度同样使用分离口径：明确结论计算安全率/风险率，人工核验单独计算比例。
                    return EvalReportRiskBreakdownVO.builder()
                            .categoryId(row.getCategoryId())
                            .categoryName(row.getCategoryName())
                            .riskDetailsId(row.getRiskDetailsId())
                            .riskDetailsName(row.getRiskDetailsName())
                            .totalCount(totalCount)
                            .safeCount(safeCount)
                            .unsafeCount(unsafeCount)
                            .manualReviewCount(manualReviewCount)
                            .failedCount(nvl(row.getFailedCount()))
                            .stoppedCount(nvl(row.getStoppedCount()))
                            .safeRate(EvalReportMetricCalculator.rate(safeCount, conclusiveCount))
                            .riskRate(EvalReportMetricCalculator.rate(unsafeCount, conclusiveCount))
                            .manualReviewRate(EvalReportMetricCalculator.rate(manualReviewCount, evaluatedCount))
                            .build();
                })
                .collect(Collectors.toList());
    }

    private List<EvalReportPipelineBreakdownVO> buildPipelineBreakdown(List<EvalReportPipelineStatRow> rows) {
        return safeList(rows).stream()
                .map(row -> EvalReportPipelineBreakdownVO.builder()
                        .nodeCode(row.getNodeCode())
                        .nodeCodeDesc(pipelineNodeCodeDesc(row.getNodeCode()))
                        .status(row.getStatus())
                        .statusDesc(pipelineNodeStatusDesc(row.getStatus()))
                        .count(nvl(row.getNodeCount()))
                        .avgLatency(row.getAvgLatency())
                        .minLatency(row.getMinLatency())
                        .maxLatency(row.getMaxLatency())
                        .build())
                .collect(Collectors.toList());
    }

    private List<EvalReportSampleSummaryVO> buildSampleSummaries(List<EvalReportSampleRow> rows) {
        return safeList(rows).stream()
                .map(row -> EvalReportSampleSummaryVO.builder()
                        .resultDetailId(row.getResultDetailId())
                        .sampleId(row.getSampleId())
                        .riskDetailsId(row.getRiskDetailsId())
                        .riskDetailsName(row.getRiskDetailsName())
                        .categoryId(row.getCategoryId())
                        .categoryName(row.getCategoryName())
                        .inputText(trimText(row.getInputText()))
                        .modelOutput(trimText(row.getModelOutput()))
                        .isSafe(row.getSafe())
                        .status(row.getStatus())
                        .statusDesc(evalResultStatusDesc(row.getStatus()))
                        .errorMsg(trimText(row.getErrorMsg()))
                        .latency(row.getLatency())
                        .updateTime(row.getUpdateTime())
                        .build())
                .collect(Collectors.toList());
    }

    private Map<Integer, Long> buildStatusCounts(List<EvalReportStatusCountRow> rows) {
        Map<Integer, Long> result = new LinkedHashMap<>();
        // 先填充所有已知状态，确保前端即使在某个状态数量为 0 时也能拿到稳定 key。
        for (EvalResultStatusEnums statusEnum : EvalResultStatusEnums.values()) {
            result.put(statusEnum.getCode(), 0L);
        }
        for (EvalReportStatusCountRow row : rows) {
            if (row.getStatus() == null) {
                continue;
            }
            result.merge(row.getStatus(), nvl(row.getSampleCount()), Long::sum);
        }
        return result;
    }

    private int resolveTotalCount(EvalTaskDetailDO taskDetail, long resultCount) {
        Integer totalCount = taskDetail.getTotalCount();
        if (totalCount != null && totalCount > 0) {
            return totalCount;
        }
        return resultCount > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) resultCount;
    }

    private long getStatusCount(Map<Integer, Long> statusCounts, EvalResultStatusEnums status) {
        return nvl(statusCounts.get(status.getCode()));
    }

    private boolean isFinalized(Integer status) {
        // 终态批次返回正式报告；非终态批次也允许生成，但 meta.finalized 会标识为快照。
        return TaskStatusEnums.COMPLETED.getCode().equals(status)
                || TaskStatusEnums.ERROR.getCode().equals(status)
                || TaskStatusEnums.STOPPED.getCode().equals(status);
    }

    private Long calculateDurationMillis(LocalDateTime startTime, LocalDateTime endTime, LocalDateTime fallbackEndTime) {
        if (startTime == null) {
            return null;
        }
        LocalDateTime actualEndTime = endTime == null ? fallbackEndTime : endTime;
        return Duration.between(startTime, actualEndTime).toMillis();
    }

    private String taskStatusDesc(Integer status) {
        if (status == null) {
            return null;
        }
        for (TaskStatusEnums statusEnum : TaskStatusEnums.values()) {
            if (statusEnum.getCode().equals(status)) {
                return statusEnum.getDesc();
            }
        }
        return "未知状态";
    }

    private String evalResultStatusDesc(Integer status) {
        if (status == null) {
            return null;
        }
        for (EvalResultStatusEnums statusEnum : EvalResultStatusEnums.values()) {
            if (statusEnum.getCode().equals(status)) {
                return statusEnum.getDesc();
            }
        }
        return "未知状态";
    }

    private String pipelineNodeCodeDesc(String nodeCode) {
        if (nodeCode == null) {
            return null;
        }
        for (PipelineNodeCodeEnums codeEnum : PipelineNodeCodeEnums.values()) {
            if (codeEnum.getCode().equals(nodeCode)) {
                return codeEnum.getDesc();
            }
        }
        return "未知节点";
    }

    private String pipelineNodeStatusDesc(String status) {
        if (status == null) {
            return null;
        }
        for (PipelineNodeStatusEnums statusEnum : PipelineNodeStatusEnums.values()) {
            if (statusEnum.getCode().equals(status)) {
                return statusEnum.getDesc();
            }
        }
        return "未知状态";
    }

    private String trimText(String text) {
        // 报告只承载问题样本摘要；完整文本仍通过结果明细分页接口查看。
        if (text == null || text.length() <= TEXT_DIGEST_LENGTH) {
            return text;
        }
        return text.substring(0, TEXT_DIGEST_LENGTH);
    }

    private long nvl(Long value) {
        return value == null ? 0L : value;
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? Collections.emptyList() : values;
    }
}
