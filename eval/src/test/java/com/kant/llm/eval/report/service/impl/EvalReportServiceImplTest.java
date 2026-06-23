package com.kant.llm.eval.report.service.impl;

import com.kant.llm.common.enums.EvalResultStatusEnums;
import com.kant.llm.common.enums.TaskStatusEnums;
import com.kant.llm.eval.basesource.dao.mapper.ModelInfoMapper;
import com.kant.llm.eval.dataset.dao.mapper.DataSetMapper;
import com.kant.llm.eval.report.dao.mapper.EvalReportMapper;
import com.kant.llm.eval.report.dao.model.EvalReportSampleRow;
import com.kant.llm.eval.report.dao.model.EvalReportStatusCountRow;
import com.kant.llm.eval.report.dto.resp.EvalTaskReportVO;
import com.kant.llm.eval.task.dao.entity.EvalTaskDetailDO;
import com.kant.llm.eval.task.dao.mapper.EvalTaskDetailMapper;
import com.kant.llm.eval.task.dao.mapper.EvalTaskMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * EvalReportServiceImpl 单元测试。
 *
 * <p>这些测试聚焦报告统计口径本身，不依赖真实数据库、Redis、RocketMQ 或外部模型。</p>
 */
@ExtendWith(MockitoExtension.class)
class EvalReportServiceImplTest {

    @Mock
    private EvalTaskDetailMapper evalTaskDetailMapper;
    @Mock
    private EvalTaskMapper evalTaskMapper;
    @Mock
    private ModelInfoMapper modelInfoMapper;
    @Mock
    private DataSetMapper dataSetMapper;
    @Mock
    private EvalReportMapper evalReportMapper;

    @Test
    void getTaskReportUsesSeparatedSafetyMetrics() {
        // 构造一个混合批次：2 条安全、1 条违规、1 条人工核验、1 条失败。
        // 期望安全率/风险率只基于 3 条明确结论计算，人工核验和失败不混入违规分母。
        EvalReportServiceImpl service = newService();
        Long taskDetailId = 100L;
        when(evalTaskDetailMapper.selectById(taskDetailId)).thenReturn(EvalTaskDetailDO.builder()
                .id(taskDetailId)
                .taskId(10L)
                .taskName("safety regression")
                .modelId(20L)
                .datasetId(30L)
                .status(TaskStatusEnums.RUNNING.getCode())
                .totalCount(5)
                .finishedCount(3)
                .startTime(LocalDateTime.now().minusMinutes(1))
                .build());
        when(evalReportMapper.countResultStatus(taskDetailId)).thenReturn(List.of(
                statusRow(EvalResultStatusEnums.AUTO_SCORED, true, 2L),
                statusRow(EvalResultStatusEnums.AUTO_SCORED, false, 1L),
                statusRow(EvalResultStatusEnums.MANUAL_REVIEWED, null, 1L),
                statusRow(EvalResultStatusEnums.FAILED, false, 1L)
        ));
        when(evalReportMapper.listUnsafeSamples(eq(taskDetailId), eq(50))).thenReturn(List.of(sampleRow(1L, false)));
        when(evalReportMapper.listManualReviewSamples(eq(taskDetailId), eq(50))).thenReturn(List.of(sampleRow(2L, null)));
        when(evalReportMapper.listFailedSamples(eq(taskDetailId), eq(50))).thenReturn(List.of(sampleRow(3L, false)));

        EvalTaskReportVO report = service.getTaskReport(taskDetailId, 999);

        assertThat(report.getSafety().getSafeCount()).isEqualTo(2L);
        assertThat(report.getSafety().getUnsafeCount()).isEqualTo(1L);
        assertThat(report.getSafety().getManualReviewCount()).isEqualTo(1L);
        assertThat(report.getSafety().getSafeRate()).isEqualByComparingTo(new BigDecimal("0.6667"));
        assertThat(report.getSafety().getRiskRate()).isEqualByComparingTo(new BigDecimal("0.3333"));
        assertThat(report.getSafety().getManualReviewRate()).isEqualByComparingTo(new BigDecimal("0.2500"));
        assertThat(report.getExecution().getFailedCount()).isEqualTo(1L);
        assertThat(report.getExecution().getFailedRate()).isEqualByComparingTo(new BigDecimal("0.2000"));
        assertThat(report.getUnsafeSamples()).hasSize(1);
        assertThat(report.getManualReviewSamples()).hasSize(1);
        assertThat(report.getFailedSamples()).hasSize(1);
        verify(evalReportMapper).listUnsafeSamples(taskDetailId, 50);
    }

    @Test
    void getTaskReportRejectsMissingTaskDetailId() {
        // taskDetailId 是报告唯一入口，缺失时应直接拒绝，避免执行无边界聚合查询。
        EvalReportServiceImpl service = newService();

        assertThatThrownBy(() -> service.getTaskReport(null, null))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("批次ID不能为空");
    }

    private EvalReportServiceImpl newService() {
        // 使用 Mockito mock 的 mapper 手动装配服务，避免启动 Spring 容器。
        return new EvalReportServiceImpl(evalTaskDetailMapper, evalTaskMapper, modelInfoMapper, dataSetMapper, evalReportMapper);
    }

    private EvalReportStatusCountRow statusRow(EvalResultStatusEnums status, Boolean safe, Long count) {
        // 构造状态聚合行，模拟 EvalReportMapper.countResultStatus 的返回结果。
        EvalReportStatusCountRow row = new EvalReportStatusCountRow();
        row.setStatus(status.getCode());
        row.setSafe(safe);
        row.setSampleCount(count);
        return row;
    }

    private EvalReportSampleRow sampleRow(Long id, Boolean safe) {
        // 构造问题样本行，模拟违规、人工核验或失败样本查询结果。
        EvalReportSampleRow row = new EvalReportSampleRow();
        row.setResultDetailId(id);
        row.setSampleId(id + 1000);
        row.setInputText("input " + id);
        row.setModelOutput("output " + id);
        row.setSafe(safe);
        row.setStatus(Boolean.FALSE.equals(safe)
                ? EvalResultStatusEnums.AUTO_SCORED.getCode()
                : EvalResultStatusEnums.MANUAL_REVIEWED.getCode());
        return row;
    }
}
