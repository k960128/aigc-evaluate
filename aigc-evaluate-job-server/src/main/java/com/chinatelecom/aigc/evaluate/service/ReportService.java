package com.chinatelecom.aigc.evaluate.service;

import com.chinatelecom.aigc.evaluate.common.pojo.PageResult;
import com.chinatelecom.aigc.evaluate.dto.req.ReportSaveReq;
import com.chinatelecom.aigc.evaluate.dto.resp.*;

import javax.servlet.http.HttpServletResponse;
import javax.validation.constraints.NotEmpty;
import java.util.List;
import java.util.Map;

public interface ReportService {

    /**
     * 创建报告
     *
     * @param createReqVO 创建报告的请求参数
     * @return 报告ID
     */
    @NotEmpty(message = "任务 ID 不能为空")
    ReportResp createReport(ReportSaveReq createReqVO);

    /**
     * 创建报告V2
     *
     * @param createReqVO 创建报告的请求参数
     * @return 报告ID
     */
    @NotEmpty(message = "任务 ID 不能为空")
    ReportResp createReportV2(ReportSaveReq createReqVO);

    /**
     * 获取报告预览
     * @param userId 用户ID
     * @param taskId 任务ID
     * @param modelId 模型ID
     * @return 报告的PDF字节数组（用于预览）
     */
    byte[] previewReport(String userId, String taskId, String modelId, String reportType);

    /**
     * 保存人工描述
     * @param userId 用户ID
     * @param taskId 任务ID
     * @param modelId 模型ID
     * @param reportType 报告类型
     */
    void saveManualDesc(String userId, String taskId, String modelId, String reportType, String manualDesc);


    /**
     * 获取人工描述
     * @param userId 用户ID
     * @param taskId 任务ID
     * @param modelId 模型ID
     * @param reportType 报告类型
     * @return 是否成功
     */
    String getManualDesc(String userId, String taskId, String modelId, String reportType);

    /**
     * 下载报告
     *
     * @param taskId   任务ID
     * @param modelId  模型ID
     * @param response response
     */
    void downloadReport(String userId, String taskId, String modelId, String fileType, HttpServletResponse response);

    /**
     * 创建报告V2
     *
     * @param createReqVO 创建报告的请求参数
     * @return 报告ID
     */
    @NotEmpty(message = "任务 ID 不能为空")
    ReportResp createReportExcel(ReportSaveReq createReqVO);

    /**
     * 分页查询报告列表
     *
     * @param req 分页查询请求参数
     * @return 报告分页响应
     */
    PageResult<ReportRespPageResp> queryReportPage(ReportSaveReq req);

    /**
     * 更新报告状态
     *
     * @param req 更新状态请求参数
     */
    void updateReportStatus(ReportSaveReq req);

    Long getUnreadReportCount(String userId);

    Map<String, ReportStatisticsResp> getReportStatistic(String taskId, String modelId);

    //Map<String, ReportStatisticsResp> getAttackCountStatistics(String taskId, String modelId);

    ReportBaseInfoResp getReportBaseData(String taskId, String modelId);

    List<TagSummaryResp> getReportTagSummary(String taskId, String modelId);

    Map<String, List<ReportPerStatisticResp>> getReportAttackMethod(String taskId, String modelId);

    Map<String, List<ReportPerStatisticResp>> getReportTagStatistic(String taskId, String modelId);

    List<TaskAnswerAbnormalResp> listAbnormalTaskAnswers(String modelId, String taskId, String category, int count);

    PageResult<TaskAnswerAbnormalResp> pageAbnormalTaskAnswers(String modelId, String taskId, String category, int page, int size);
}
