package com.chinatelecom.aigc.evaluate.controller;

import com.chinatelecom.aigc.evaluate.common.pojo.CommonResult;
import com.chinatelecom.aigc.evaluate.dto.req.ReportSaveReq;
import com.chinatelecom.aigc.evaluate.dto.resp.*;
import com.chinatelecom.aigc.evaluate.service.ReportService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;

import static com.chinatelecom.aigc.evaluate.common.exception.enums.ErrorCodeConstants.REPORT_TYPE_ERROR;
import static com.chinatelecom.aigc.evaluate.common.exception.util.ServiceExceptionUtil.exception;
import static com.chinatelecom.aigc.evaluate.common.pojo.CommonResult.success;
import com.chinatelecom.aigc.evaluate.common.pojo.PageResult;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/aigc/evaluate/report")
@Api(tags = "报告管理-report")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @PostMapping("/generate")
    @ApiOperation("创建报告")
    public CommonResult<ReportResp> createReport(
            @RequestHeader("User") String userId,
            @Valid @RequestBody ReportSaveReq createReqVO) {
        createReqVO.setUserId(userId);

        String reportType = createReqVO.getReportType();
        if ("excel".equalsIgnoreCase(reportType)) {
            return success(reportService.createReportExcel(createReqVO));
        } else if ("pdf".equalsIgnoreCase(reportType)) {
            return success(reportService.createReportV2(createReqVO));
        } else {
            throw exception(REPORT_TYPE_ERROR, reportType);
        }
    }


    @PostMapping("/generate/v2")
    @ApiOperation("创建报告")
    public CommonResult<ReportResp> createReportV2(
            @RequestHeader("User") String userId,
            @Valid @RequestBody ReportSaveReq createReqVO) {
        createReqVO.setUserId(userId);

        return success(reportService.createReportV2(createReqVO));
    }

    @GetMapping("/preview")
    @ApiOperation("预览报告")
    public CommonResult<byte[]> previewReport(
            @RequestHeader("User") String userId,
            @ApiParam(value = "任务ID", required = true) @RequestParam("taskId") String taskId,
            @ApiParam(value = "模型ID", required = true) @RequestParam("modelId") String modelId,
            @ApiParam(value = "文件类型(pdf excel)", required = true) @RequestParam("fileType") String fileType) {
        byte[] reportBytes = reportService.previewReport(userId, taskId, modelId, fileType);
        return success(reportBytes);
    }

    @PostMapping("/manual-desc/save")
    @ApiOperation("人工描述保存")
    public CommonResult<Boolean> saveManualDesc(
            @RequestHeader("User") String userId,
            @ApiParam(value = "任务ID", required = true) @RequestParam("taskId") String taskId,
            @ApiParam(value = "模型ID", required = true) @RequestParam("modelId") String modelId,
            @ApiParam(value = "文件类型(pdf excel)", required = true) @RequestParam("fileType") String fileType,
            @ApiParam(value = "人工描述", required = true) @RequestParam("manualDesc") String manualDesc) {
        reportService.saveManualDesc(userId, taskId, modelId, fileType, manualDesc);
        return CommonResult.success("保存成功", 0);
    }

    @GetMapping("/manual-desc/get")
    @ApiOperation("人工描述获取")
    public CommonResult<String> getManualDesc(
            @RequestHeader("User") String userId,
            @ApiParam(value = "任务ID", required = true) @RequestParam("taskId") String taskId,
            @ApiParam(value = "模型ID", required = true) @RequestParam("modelId") String modelId,
            @ApiParam(value = "文件类型(pdf excel)", required = true) @RequestParam("fileType") String fileType) {
        String desc = reportService.getManualDesc(userId, taskId, modelId, fileType);
        return success(desc);
    }

    @GetMapping("/download")
    @ApiOperation("下载报告")
    public void downloadExcelReport(
            @RequestHeader("User") String userId,
            @ApiParam(value = "任务ID", required = true) @RequestParam("taskId") String taskId,
            @ApiParam(value = "模型ID", required = true) @RequestParam("modelId") String modelId,
            @ApiParam(value = "文件类型(pdf excel)", required = true) @RequestParam("fileType") String fileType,
                HttpServletResponse response) {

        if ("excel".equalsIgnoreCase(fileType)) {
            reportService.downloadReport(userId, taskId, modelId, fileType, response);
        } else if ("pdf".equalsIgnoreCase(fileType)) {
            reportService.downloadReport(userId, taskId, modelId, fileType, response);
        } else {
            throw exception(REPORT_TYPE_ERROR, fileType);
        }
    }

    @PostMapping("/page")
    @ApiOperation("分页查询报告列表")
    public CommonResult<PageResult<ReportRespPageResp>> queryReportPage(
            @RequestHeader("User") String userId,
            @Valid @RequestBody ReportSaveReq queryReq) {
        queryReq.setUserId(userId);
        return success(reportService.queryReportPage(queryReq));
    }


    @PostMapping("/updateStatus")
    @ApiOperation("更新报告状态")
    public CommonResult<Boolean> updateReportStatus(
            @RequestHeader("User") String userId,
            @Valid @RequestBody ReportSaveReq updateReq) {
        updateReq.setUserId(userId);

        reportService.updateReportStatus(updateReq);
        return success(true);
    }

    @PostMapping("/unread/count")
    @ApiOperation("获取当前用户未读报告数量")
    public CommonResult<Long> getUnreadReportCount(
            @RequestHeader("User") String userId,
            @Valid @RequestBody ReportSaveReq reportReq) {
        reportReq.setUserId(userId);

        Long count = reportService.getUnreadReportCount(userId);
        return success(count);
    }


    @PostMapping("/getTagCountStatistics")
    @ApiOperation("报告标签数量统计")
    public CommonResult<Map<String, ReportStatisticsResp>> getReportStatistic(
            @ApiParam(value = "任务ID", required = true) @RequestParam("taskId") String taskId,
            @ApiParam(value = "模型ID", required = true) @RequestParam("modelId") String modelId) {

        Map<String, ReportStatisticsResp> statisticsList = reportService.getReportStatistic(taskId, modelId);

        // 返回封装结果
        return CommonResult.success(statisticsList);
    }

    /*
    @PostMapping("/getAttackCountStatistics")
    @ApiOperation("报告攻击方式数量统计")
    public CommonResult<Map<String, ReportStatisticsResp>> getAttackCountStatistics(
            @ApiParam(value = "任务ID", required = true) @RequestParam("taskId") String taskId,
            @ApiParam(value = "模型ID", required = true) @RequestParam("modelId") String modelId) {

        Map<String, ReportStatisticsResp> statisticsList = reportService.getAttackCountStatistics(taskId, modelId);

        // 返回封装结果
        return CommonResult.success(statisticsList);
    }
     */

    @PostMapping("/getBase")
    @ApiOperation("报告基础信息")
    public CommonResult<ReportBaseInfoResp> getReportBaseData(
            @ApiParam(value = "任务ID", required = true) @RequestParam("taskId") String taskId,
            @ApiParam(value = "模型ID", required = true) @RequestParam("modelId") String modelId) {

        ReportBaseInfoResp reportBaseData = reportService.getReportBaseData(taskId, modelId);

        // 返回封装结果
        return CommonResult.success(reportBaseData);
    }

    @PostMapping("/getTagSummary")
    @ApiOperation("报告数据概况")
    public CommonResult<List<TagSummaryResp>> getReportTagSummary(
            @ApiParam(value = "任务ID", required = true) @RequestParam("taskId") String taskId,
            @ApiParam(value = "模型ID", required = true) @RequestParam("modelId") String modelId) {

        List<TagSummaryResp> tagSummaryRespList = reportService.getReportTagSummary(taskId, modelId);

        // 返回封装结果
        return CommonResult.success(tagSummaryRespList);
    }

    @PostMapping("/getAttackMethod")
    @ApiOperation("报告攻击方式统计")
    public CommonResult<Map<String, List<ReportPerStatisticResp>>> getReportAttackMethod(
            @ApiParam(value = "任务ID", required = true) @RequestParam("taskId") String taskId,
            @ApiParam(value = "模型ID", required = true) @RequestParam("modelId") String modelId) {

        Map<String, List<ReportPerStatisticResp>> mapReportPerStat = reportService.getReportAttackMethod(taskId, modelId);

        // 返回封装结果
        return CommonResult.success(mapReportPerStat);
    }

    @PostMapping("/getTagPerStatistic")
    @ApiOperation("报告标签比例统计")
    public CommonResult<Map<String, List<ReportPerStatisticResp>>> getTagStatistic(
            @ApiParam(value = "任务ID", required = true) @RequestParam("taskId") String taskId,
            @ApiParam(value = "模型ID", required = true) @RequestParam("modelId") String modelId) {

        Map<String, List<ReportPerStatisticResp>> mapReportPerStat = reportService.getReportTagStatistic(taskId, modelId);

        // 返回封装结果
        return CommonResult.success(mapReportPerStat);
    }

    @GetMapping("/abnormal/list")
    @ApiOperation("异常问题列表")
    public CommonResult<List<TaskAnswerAbnormalResp>> listAbnormalAnswers(
            @ApiParam(value = "任务ID", required = true) @RequestParam("taskId") String taskId,
            @ApiParam(value = "模型ID", required = true) @RequestParam("modelId") String modelId,
            @ApiParam(value = "FORWARD | NEGATIVE", required = true) @RequestParam("category") String category,
            @ApiParam(value = "数量", required = true) @RequestParam(defaultValue = "10") int count) {
        return CommonResult.success(reportService.listAbnormalTaskAnswers(modelId, taskId, category, count));
    }

    @GetMapping("/abnormal/page")
    @ApiOperation("异常问题列表（分页）")
    public CommonResult<PageResult<TaskAnswerAbnormalResp>> listAbnormalAnswersPage(
            @ApiParam(value = "任务ID", required = true) @RequestParam("taskId") String taskId,
            @ApiParam(value = "模型ID", required = true) @RequestParam("modelId") String modelId,
            @ApiParam(value = "FORWARD | NEGATIVE", required = true) @RequestParam("category") String category,
            @ApiParam(value = "页码", required = true) @RequestParam(defaultValue = "1") int page,
            @ApiParam(value = "每页数量", required = true) @RequestParam(defaultValue = "10") int size) {
        return CommonResult.success(reportService.pageAbnormalTaskAnswers(modelId, taskId, category, page, size));
    }

}
