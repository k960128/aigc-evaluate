package com.chinatelecom.aigc.evaluate.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSON;
import com.chinatelecom.aigc.evaluate.common.config.QualifiedStandardConfiguration;
import com.chinatelecom.aigc.evaluate.common.enums.JudgeResultEnum;
import com.chinatelecom.aigc.evaluate.common.enums.QuestionCategoryEnum;
import com.chinatelecom.aigc.evaluate.common.enums.QuestionDifficultyEnum;
import com.chinatelecom.aigc.evaluate.common.enums.ReportAnswerColorEnum;
import com.chinatelecom.aigc.evaluate.common.exception.enums.ErrorCodeConstants;
import com.chinatelecom.aigc.evaluate.common.util.date.DateUtils;
import com.chinatelecom.aigc.evaluate.common.util.file.FileUtil;
import com.chinatelecom.aigc.evaluate.common.util.snow.CodeUtils;
import com.chinatelecom.aigc.evaluate.domain.*;
import com.chinatelecom.aigc.evaluate.dto.model.QuestionKey;
import com.chinatelecom.aigc.evaluate.dto.model.TaskReportStep;
import com.chinatelecom.aigc.evaluate.dto.req.ReportSaveReq;
import com.chinatelecom.aigc.evaluate.dto.resp.*;
import com.chinatelecom.aigc.evaluate.jfree.CustomLabelGenerator;
import com.chinatelecom.aigc.evaluate.jfree.CustomRingPlot;
import com.chinatelecom.aigc.evaluate.listener.PageNumberingEventListener;
import com.chinatelecom.aigc.evaluate.mapper.QuestionMapper;
import com.chinatelecom.aigc.evaluate.mapper.QuestionTagInfoMapper;
import com.chinatelecom.aigc.evaluate.mapper.ReportInfoMapper;
import com.chinatelecom.aigc.evaluate.mapper.TaskAnswerMapper;
import com.chinatelecom.aigc.evaluate.mq.execute.ExecutorQueueService;
import com.chinatelecom.aigc.evaluate.mq.param.MessageBody;
import com.chinatelecom.aigc.evaluate.service.*;
import com.itextpdf.text.*;
import com.itextpdf.text.Font;
import com.itextpdf.text.Image;
import com.itextpdf.text.pdf.*;
import com.itextpdf.text.pdf.draw.LineSeparator;
import com.itextpdf.text.pdf.draw.VerticalPositionMark;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.ui.HorizontalAlignment;
import org.jfree.data.general.DefaultPieDataset;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletResponse;
import javax.validation.constraints.NotEmpty;
import java.awt.*;
import java.io.*;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chinatelecom.aigc.evaluate.common.pojo.PageResult;
import com.chinatelecom.aigc.evaluate.common.util.object.BeanUtils;

import org.apache.commons.collections4.CollectionUtils;

import static com.chinatelecom.aigc.evaluate.common.exception.enums.ErrorCodeConstants.*;
import static com.chinatelecom.aigc.evaluate.common.exception.util.ServiceExceptionUtil.exception;


@Service
@Slf4j
public class ReportServiceImpl implements ReportService {

    private static final Integer CELL_HEIGHT = 30;
    // 默认背景颜色
    private static final BaseColor BACKGROUND_COLOR = new BaseColor(237, 252, 237);
    // 默认主题颜色
    private static final BaseColor DEFAULT_THEME_COLOR = new BaseColor(101, 159, 101);

    private final TaskAnswerService taskAnswerService;
    private final JobService jobService;
    private final ReportInfoMapper reportInfoMapper;
    private final QuestionSetService questionSetService;
    private final QuestionSetItemService questionSetItemService;
    private final ExecutorQueueService executorQueueService;
    private final TaskAnswerMapper taskAnswerMapper;
    private final QuestionTagInfoMapper questionTagInfoMapper;
    private final QuestionMapper questionMapper;
    private final QualifiedStandardConfiguration qualifiedStandardConfiguration;
    public ReportServiceImpl(TaskAnswerService taskAnswerService,
                             JobService jobService,
                             ReportInfoMapper reportInfoMapper,
                             QuestionSetService questionSetService,
                             QuestionSetItemService questionSetItemService,
                             ExecutorQueueService executorQueueService,
                             TaskAnswerMapper taskAnswerMapper,
                             QuestionTagInfoMapper questionTagInfoMapper,
                             QuestionMapper questionMapper,
                             QualifiedStandardConfiguration qualifiedStandardConfiguration) {
        this.taskAnswerService = taskAnswerService;
        this.jobService = jobService;
        this.reportInfoMapper = reportInfoMapper;
        this.questionSetService = questionSetService;
        this.questionSetItemService = questionSetItemService;
        this.executorQueueService = executorQueueService;
        this.taskAnswerMapper = taskAnswerMapper;
        this.questionTagInfoMapper = questionTagInfoMapper;
        this.questionMapper = questionMapper;
        this.qualifiedStandardConfiguration = qualifiedStandardConfiguration;
    }

    /**
     * 创建报告
     *
     * @param createReqVO 创建报告的请求参数
     * @return 报告ID
     */
    @Override
    public @NotEmpty(message = "任务 ID 不能为空") ReportResp createReport(ReportSaveReq createReqVO) {
        long reportId = System.currentTimeMillis();
        // 生成报告保存路径
        String reportPath = FileUtil.getReportPath() + createReqVO.getName() + "-" + reportId + ".pdf";
        // 获取任务基本信息
        JobResp jobInfo = jobService.getJob(Long.valueOf(createReqVO.getTaskId()));
        if (jobInfo != null && jobInfo.getModelInfoMap().containsKey(Long.valueOf(createReqVO.getModelId()))) {
            // 获取生成报告数据
            EvaluateResultStatisticsGroupResp statistics = taskAnswerService.getEvaluateResultStatisticsByTaskIdAndModelId(Long.parseLong(createReqVO.getTaskId()),
                    Long.parseLong(createReqVO.getModelId()));
            // 获取模型数据
            ModelInfoDO modelInfoDO = jobInfo.getModelInfoMap().get(Long.valueOf(createReqVO.getModelId()));
            // 获取题集信息
            QuestionSetInfoDO questionSetInfo = questionSetService.get(jobInfo.getQuestionSet().get(0).getId(), true);
            // 获取题集题目数量分布数据
            List<QuestionSetItemStatisticsResp> questionSetStatistics = questionSetItemService.getQuestionSetStatistic(jobInfo.getQuestionSet().get(0).getId(), true);
            // 收集异常题目详情
            Map<String, List<TaskAnswerAbnormalResp>> taskAnswerByAbnormalViolation = taskAnswerService.getTaskAnswerByAbnormalViolation(jobInfo.getId(), modelInfoDO.getId());
            Document document = null;
            try {
                Path dataPath = Paths.get(FileUtil.getReportPath());
                // 判断路径是否存在，不存在则创建
                if (!Files.exists(dataPath)) {
                    Files.createDirectories(dataPath);
                }
                document = new Document();
                PdfWriter pdfWriter = PdfWriter.getInstance(document, Files.newOutputStream(Paths.get(reportPath)));
                pdfWriter.setPageEvent(new PageNumberingEventListener());
                document.open();
                // 生成 Document 并添加内容
                generateDocument(document, jobInfo, modelInfoDO, statistics, questionSetInfo, questionSetStatistics, taskAnswerByAbnormalViolation);
                log.info("报告创建成功，报告ID: {}, 路径: {}", createReqVO.getName(), reportPath);
            } catch (IOException e) {
                log.error("生成报告失败，报告内容: {}", createReqVO.getName(), e);
                // 可以自定义业务异常或者根据情况抛出其他异常
                throw exception(REPORT_GENERATE_ERROR, e.getMessage());
            } catch (DocumentException e) {
                throw exception(REPORT_GENERATE_ERROR, e.getMessage());

            } finally {
                if (document != null) {
                    document.close();
                }
            }
        } else {
            throw exception(REPORT_CREATE_ERROR);
        }
        // 返回生成的报告 ID
        ReportInfoDO reportInfoDO = reportInfoMapper.selectReport(createReqVO.getUserId(), createReqVO.getTaskId(), createReqVO.getModelId(), createReqVO.getReportType());
        // 如果存在，先删除后新增
        if (ObjectUtil.isNotNull(reportInfoDO)) {
            reportInfoMapper.deleteById(reportInfoDO);
        }
        reportInfoDO = ReportInfoDO.create(createReqVO);
        reportInfoDO.setFilePath(reportPath);
        reportInfoMapper.insert(reportInfoDO);
        return ReportResp.builder()
                .id(reportInfoDO.getId())
                .taskId(String.valueOf(reportInfoDO.getTaskId()))
                .modelId(String.valueOf(reportInfoDO.getModelId()))
                .name(reportInfoDO.getFileName())
                .filePath(reportInfoDO.getFilePath())
                .build();
    }

    /**
     * 创建报告V2
     *
     * @param createReqVO 创建报告的请求参数
     * @return 报告ID
     */
    @Override
    public @NotEmpty(message = "任务 ID 不能为空") ReportResp createReportV2(ReportSaveReq createReqVO) {
        // 获取任务基本信息
        JobResp jobInfo = jobService.getJob(Long.valueOf(createReqVO.getTaskId()));
        if (jobInfo != null && jobInfo.getModelInfoMap().containsKey(Long.valueOf(createReqVO.getModelId()))) {
            // 生产消息
            executorQueueService.product("REPORT_PDF",
                    new MessageBody(String.valueOf(CodeUtils.getSnowFlakeId()),
                            createReqVO.getUserId(),
                            JSON.toJSONString(TaskReportStep.builder()
                                    .jobInfo(jobInfo)
                                    .userId(createReqVO.getUserId())
                                    .taskId(createReqVO.getTaskId())
                                    .modelId(createReqVO.getModelId())
                                    .name(createReqVO.getName())
                                    .reportType(createReqVO.getReportType())
                                    .build())
                    ));

            // 获取模型数据
            ModelInfoDO modelInfoDO = jobInfo.getModelInfoMap().get(Long.valueOf(createReqVO.getModelId()));

            // 查询任务名称
            String jobName = jobInfo.getName();
            String modelName = modelInfoDO != null ? modelInfoDO.getModelName() : String.valueOf(createReqVO.getModelId());

            String message = String.format("任务[%s]、模型[%s] 的报告[%s.pdf] 已开始生成，请耐心等待生成完成后下载或查看。",
                    jobName,
                    modelName,
                    createReqVO.getName()
            );

            return ReportResp.builder()
                    .userId(createReqVO.getUserId())
                    .taskId(createReqVO.getTaskId())
                    .modelId(createReqVO.getModelId())
                    .name(createReqVO.getName())
                    .desc(message)
                    .build();
        } else {
            throw exception(REPORT_CREATE_ERROR);
        }
        /*
        // 返回生成的报告信息
        ReportInfoDO reportInfoDO = reportInfoMapper.selectByTaskIdAndModelId(createReqVO.getTaskId(), createReqVO.getModelId());
        return ReportResp.builder()
                .id(reportInfoDO.getId())
                .taskId(String.valueOf(reportInfoDO.getTaskId()))
                .modelId(String.valueOf(reportInfoDO.getModelId()))
                .name(reportInfoDO.getFileName())
                .filePath(reportInfoDO.getFilePath())
                .build();
         */
    }


    /**
     * 获取报告预览
     *
     * @param taskId 任务ID
     * modelId 模型ID
     * @return 报告的PDF字节数组（用于预览）
     */
    @Override
    public byte[] previewReport(String userId, String taskId, String modelId, String reportType) {
        Assert.notNull(taskId, "任务ID不能为空");
        Assert.notNull(modelId, "模型ID不能为空");
        Assert.notNull(taskId, "用户ID不能为空");
        Assert.notNull(reportType, "报告类型不能为空");
        ReportInfoDO reportInfoDO = reportInfoMapper.selectReport(userId, taskId, modelId, reportType);
        if (ObjectUtil.isNull(reportInfoDO)) {
            throw exception(REPORT_PREVIEW_ERROR);
        }
        return readPdfFileAsByteArray(reportInfoDO.getFilePath());
    }


    @Override
    public void saveManualDesc(String userId, String taskId, String modelId, String reportType, String manualDesc){
        Assert.notNull(taskId, "任务ID不能为空");
        Assert.notNull(modelId, "模型ID不能为空");
        Assert.notNull(taskId, "用户ID不能为空");
        Assert.notNull(reportType, "报告类型不能为空");

        LocalDateTime now = LocalDateTime.now();
        ReportInfoDO reportInfoDO = reportInfoMapper.selectReport(userId, taskId, modelId, reportType);
        if (ObjectUtil.isNull(reportInfoDO)) {
            // 没查到就新建
            reportInfoDO = new ReportInfoDO();
            reportInfoDO.setTaskId(Long.parseLong(taskId));
            reportInfoDO.setModelId(Long.parseLong(modelId));
            reportInfoDO.setReportType(reportType);
            reportInfoDO.setUserId(Long.parseLong(userId));
            reportInfoDO.setManualDesc(manualDesc);
            reportInfoDO.setFileName("");
            reportInfoDO.setFilePath("");
            reportInfoDO.setCreateTime(now);
            reportInfoDO.setUpdateTime(now);
            reportInfoDO.setCreator(userId);
            reportInfoDO.setUpdater(userId);
            reportInfoDO.setDeleted(false);

            reportInfoMapper.insert(reportInfoDO);
        } else {
            // 设置人工描述并更新
            reportInfoDO.setManualDesc(manualDesc);
            reportInfoDO.setUpdater(userId);
            reportInfoDO.setUpdateTime(now);

            reportInfoMapper.updateById(reportInfoDO);
        }
    }

    @Override
    public String getManualDesc(String userId, String taskId, String modelId, String reportType){
        Assert.notNull(taskId, "任务ID不能为空");
        Assert.notNull(modelId, "模型ID不能为空");
        Assert.notNull(taskId, "用户ID不能为空");
        Assert.notNull(reportType, "报告类型不能为空");

        ReportInfoDO reportInfoDO = reportInfoMapper.selectReport(userId, taskId, modelId, reportType);
        if (ObjectUtil.isNull(reportInfoDO)) {
            return "";
        }

        return reportInfoDO.getManualDesc(); // 补充返回
    }

    /**
     * 下载报告
     *
     * @param taskId   任务ID
     *                 modelId 模型ID
     * 报告的PDF字节数组（用于下载）
     */
    @Override
    public void downloadReport(String userId, String taskId, String modelId, String fileType, HttpServletResponse response) {
        Assert.notNull(userId, "用户ID不能为空");
        Assert.notNull(taskId, "任务ID不能为空");
        Assert.notNull(modelId, "模型ID不能为空");
        Assert.notNull(fileType, "文件类型不能为空"); // 比如 "pdf" 或 "xlsx"

        ReportInfoDO reportInfoDO = reportInfoMapper.selectReport(userId, taskId, modelId, fileType);
        if (ObjectUtil.isNull(reportInfoDO)) {
            throw exception(REPORT_DOWNLOAD_ERROR);
        }

        InputStream inputStream = null;
        OutputStream outputStream = null;
        try {
            // 读取文件内容
            inputStream = new ByteArrayInputStream(readPdfFileAsByteArray(reportInfoDO.getFilePath())); // 或者修改为 readFileAsByteArray()

            // 处理文件名和响应头
            String fileName = URLEncoder.encode(reportInfoDO.getFileName() + "." + fileType, "utf-8");
            response.setHeader("Content-Disposition", "attachment;filename=" + fileName);
            response.setHeader("content-Type", "application/octet-stream;charset=utf-8");

            outputStream = response.getOutputStream();
            byte[] buffer = new byte[1024];
            int len;
            while ((len = inputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, len);
            }
        } catch (IOException e) {
            log.error("报告下载失败，失败原因:{}", e.getMessage());
            throw exception(ErrorCodeConstants.EXCEL_DOWNLOAD_ERROR, e);
        } finally {
            try {
                if (outputStream != null) {
                    outputStream.close();
                }
                if (inputStream != null) {
                    inputStream.close();
                }
            } catch (Exception e) {
                log.warn("流关闭异常:{}", e.getMessage());
            }
        }
    }


    /**
     * 创建Excel报告
     *
     * @param createReqVO 创建报告的请求参数
     * @return 报告ID
     */
    @Override
    public @NotEmpty(message = "任务 ID 不能为空") ReportResp createReportExcel(ReportSaveReq createReqVO) {
        // 获取任务基本信息
        JobResp jobInfo = jobService.getJob(Long.valueOf(createReqVO.getTaskId()));
        if (jobInfo != null && jobInfo.getModelInfoMap().containsKey(Long.valueOf(createReqVO.getModelId()))) {
            // 生产消息
            executorQueueService.product("REPORT_EXCEL",
                    new MessageBody(String.valueOf(CodeUtils.getSnowFlakeId()),
                            createReqVO.getUserId(),
                            JSON.toJSONString(TaskReportStep.builder()
                                    .jobInfo(jobInfo)
                                    .userId(createReqVO.getUserId())
                                    .taskId(createReqVO.getTaskId())
                                    .modelId(createReqVO.getModelId())
                                    .name(createReqVO.getName())
                                    .reportType(createReqVO.getReportType())
                                    .build())
                    ));


            // 获取模型数据
            ModelInfoDO modelInfoDO = jobInfo.getModelInfoMap().get(Long.valueOf(createReqVO.getModelId()));

            // 查询任务名称
            String jobName = jobInfo.getName();
            String modelName = modelInfoDO != null ? modelInfoDO.getModelName() : String.valueOf(createReqVO.getModelId());

            String message = String.format("任务[%s]、模型[%s] 的报告[%s.xlsx] 已开始生成，请耐心等待生成完成后下载或查看。",
                    jobName,
                    modelName,
                    createReqVO.getName()
            );

            return ReportResp.builder()
                    .userId(createReqVO.getUserId())
                    .taskId(createReqVO.getTaskId())
                    .modelId(createReqVO.getModelId())
                    .name(createReqVO.getName())
                    .desc(message)
                    .build();
        } else {
            throw exception(REPORT_CREATE_ERROR);
        }
        /*
        // 返回生成的报告信息
        ReportInfoDO reportInfoDO = reportInfoMapper.selectByTaskIdAndModelId(createReqVO.getTaskId(), createReqVO.getModelId());
        return ReportResp.builder()
                .id(reportInfoDO.getId())
                .taskId(String.valueOf(reportInfoDO.getTaskId()))
                .modelId(String.valueOf(reportInfoDO.getModelId()))
                .name(reportInfoDO.getFileName())
                .filePath(reportInfoDO.getFilePath())
                .build();
         */
    }


    @Override
    public PageResult<ReportRespPageResp> queryReportPage(ReportSaveReq req) {
        // 分页查询
        Page<ReportInfoDO> page = new Page<>(req.getPageNo(), req.getPageSize());

        LambdaQueryWrapper<ReportInfoDO> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(req.getId() != null, ReportInfoDO::getId, req.getId());
        queryWrapper.eq(req.getTaskId() != null, ReportInfoDO::getTaskId, req.getTaskId());
        queryWrapper.eq(req.getModelId() != null, ReportInfoDO::getModelId, req.getModelId());
        queryWrapper.eq(req.getUserId() != null, ReportInfoDO::getUserId, req.getUserId());
        queryWrapper.eq(req.getReportType() != null, ReportInfoDO::getReportType, req.getReportType());
        queryWrapper.eq(req.getReadingStatus() != null, ReportInfoDO::getReadingStatus, req.getReadingStatus());
        queryWrapper.orderByDesc(ReportInfoDO::getCreateTime);

        reportInfoMapper.selectPage(page, queryWrapper);

        // 转换结果
        List<ReportRespPageResp> records = page.getRecords().stream().map(item -> {
            ReportRespPageResp resp = new ReportRespPageResp();
            BeanUtils.copyProperties(item, resp);
            return resp;
        }).collect(Collectors.toList());

        return new PageResult<>(records, page.getTotal());
    }

    @Override
    public void updateReportStatus(ReportSaveReq req) {
        ReportInfoDO report = reportInfoMapper.selectById(req.getId());
        if (report == null) {
            throw exception(REPORT_NOT_FOUND, req.getId());
        }
        report.setReadingStatus(req.getReadingStatus());
        reportInfoMapper.updateById(report);
    }

    @Override
    public Long getUnreadReportCount(String userId){
        LambdaQueryWrapper<ReportInfoDO> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ReportInfoDO::getUserId, Long.valueOf(userId));
        queryWrapper.eq(ReportInfoDO::getReadingStatus, false);

        return reportInfoMapper.selectCount(queryWrapper);
    }

    /*
    @Override
    public Map<String, ReportStatisticsResp> getReportStatistic(String taskId, String modelId) {
        List<TaskAnswerWithQuestionInfoVO> taskAnswerDOList = getMergedTaskAnswers(Long.valueOf(taskId), Long.valueOf(modelId));
        Map<String, ReportStatisticsResp> resultMap = new HashMap<>();

        if (CollectionUtil.isNotEmpty(taskAnswerDOList)) {
            Map<String, QuestionTagInfoDO> tagInfoDOMap = questionTagInfoMapper.selectList().stream()
                    .collect(Collectors.toMap(QuestionTagInfoDO::getTagId, tagInfo -> tagInfo));

            Map<QuestionCategoryEnum, Map<QuestionDifficultyEnum, Long>> categoryCountMap = new HashMap<>();
            Map<QuestionCategoryEnum, Map<String, TagStatisticsResp>> categoryTagMap = new HashMap<>();
            Map<QuestionCategoryEnum, Map<String, Long>> categoryAttackMap = new HashMap<>();
            Map<QuestionCategoryEnum, Long> qualifiedCountMap = new HashMap<>();
            Map<QuestionCategoryEnum, Map<String, Integer>> tagQualifiedCountMap = new HashMap<>();

            for (TaskAnswerWithQuestionInfoVO answerVO : taskAnswerDOList) {
                String categoryStr = answerVO.getQuestionCategory();
                String difficultyStr = answerVO.getDifficulty();
                String tags = answerVO.getTags();
                String attackMethod = answerVO.getAttackMethod();
                Integer judgeResult = answerVO.getJudgeResult();

                QuestionCategoryEnum category = QuestionCategoryEnum.valueOf(categoryStr);
                QuestionDifficultyEnum difficulty = QuestionDifficultyEnum.valueOf(difficultyStr);

                // 1. 难度统计
                categoryCountMap.computeIfAbsent(category, k -> new HashMap<>());
                Map<QuestionDifficultyEnum, Long> difficultyMap = categoryCountMap.get(category);
                difficultyMap.put(difficulty, difficultyMap.getOrDefault(difficulty, 0L) + 1);

                // 2. 标签统计
                categoryTagMap.computeIfAbsent(category, k -> new HashMap<>());
                Map<String, TagStatisticsResp> tagMap = categoryTagMap.get(category);

                // 用于存储合格标签统计
                tagQualifiedCountMap.computeIfAbsent(category, k -> new HashMap<>());
                Map<String, Integer> qualifiedTagMap = tagQualifiedCountMap.get(category);

                if (StrUtil.isNotBlank(tags)) {
                    String[] parts = tags.trim().split(",");
                    if (parts.length == 2) {
                        String parentId = parts[0].trim();
                        String childId = parts[1].trim();

                        QuestionTagInfoDO parentTagInfo = tagInfoDOMap.get(parentId);
                        QuestionTagInfoDO childTagInfo = tagInfoDOMap.get(childId);
                        if (parentTagInfo != null && childTagInfo != null) {
                            String parentName = parentTagInfo.getTagName();
                            String childName = childTagInfo.getTagName();

                            TagStatisticsResp parentTag = tagMap.computeIfAbsent(parentName, k -> {
                                TagStatisticsResp tag = new TagStatisticsResp(parentName, 0);
                                tag.setChildren(new ArrayList<>());
                                return tag;
                            });
                            parentTag.setCount(parentTag.getCount() + 1);

                            Optional<TagStatisticsResp> child = parentTag.getChildren().stream()
                                    .filter(c -> c.getName().equals(childName)).findFirst();
                            if (child.isPresent()) {
                                child.get().setCount(child.get().getCount() + 1);
                            } else {
                                parentTag.getChildren().add(new TagStatisticsResp(childName, 1));
                            }

                            // 合格标签统计
                            String qualified = JudgeResultEnum.getQualifiedStatus(judgeResult, categoryStr);
                            if ("合格".equals(qualified)) {
                                qualifiedTagMap.put(childName, qualifiedTagMap.getOrDefault(childName, 0) + 1);
                            }
                        }
                    }
                }

                // 3. 攻击方式统计
                categoryAttackMap.computeIfAbsent(category, k -> new HashMap<>());
                Map<String, Long> attackMap = categoryAttackMap.get(category);
                if (StrUtil.isNotBlank(attackMethod)) {
                    attackMap.put(attackMethod, attackMap.getOrDefault(attackMethod, 0L) + 1);
                }

                // 4. 合格题统计（题集维度）
                String qualified = JudgeResultEnum.getQualifiedStatus(judgeResult, categoryStr);
                if ("合格".equals(qualified)) {
                    qualifiedCountMap.put(category, qualifiedCountMap.getOrDefault(category, 0L) + 1);
                }
            }

            for (QuestionCategoryEnum category : categoryCountMap.keySet()) {
                ReportStatisticsResp stat = new ReportStatisticsResp();
                stat.setName(category.name());

                Map<QuestionDifficultyEnum, Long> difficultyMap = categoryCountMap.get(category);
                long total = difficultyMap.values().stream().mapToLong(Long::longValue).sum();
                long qualified = qualifiedCountMap.getOrDefault(category, 0L);

                stat.setTotal(total);
                stat.setHard(difficultyMap.getOrDefault(QuestionDifficultyEnum.HARD, 0L));
                stat.setMedium(difficultyMap.getOrDefault(QuestionDifficultyEnum.MEDIUM, 0L));
                stat.setSimple(difficultyMap.getOrDefault(QuestionDifficultyEnum.SIMPLE, 0L));

                // 计算总体合格率
                String per = total == 0 ? "0%" : String.format("%.2f%%", qualified * 100.0 / total);

                // 使用配置判断是否合格
                boolean isQualified = qualifiedStandardConfiguration.isTotalQualified(category, (int)qualified, total);
                String result = isQualified ? "合格" : "不合格";
                String standard = qualifiedStandardConfiguration.getTotalStandardString(category);

                stat.setPer(per);
                stat.setResult(result);
                stat.setStandard(standard);

                // 设置标签统计 + 合格率等
                List<TagStatisticsResp> tagList = new ArrayList<>(categoryTagMap.getOrDefault(category, Collections.emptyMap()).values());
                Map<String, Integer> qualifiedTagMap = tagQualifiedCountMap.getOrDefault(category, Collections.emptyMap());

                for (TagStatisticsResp parentTag : tagList) {
                    for (TagStatisticsResp child : parentTag.getChildren()) {
                        long totalCount = child.getCount();
                        int qualifiedCount = qualifiedTagMap.getOrDefault(child.getName(), 0);

                        String tagPer = (totalCount == 0) ? "0%" : String.format("%.2f%%", 100.0 * qualifiedCount / totalCount);
                        String tagResult = qualifiedStandardConfiguration.isTagQualified(category, qualifiedCount, totalCount) ? "合格" : "不合格";
                        String tagStandard = qualifiedStandardConfiguration.getTagStandardString(category);

                        child.setPer(tagPer);
                        child.setResult(tagResult);
                        child.setStandard(tagStandard);
                    }
                }

                stat.setTagStatistics(tagList);
                stat.setAttackMethodStatistics(categoryAttackMap.getOrDefault(category, Collections.emptyMap()));

                resultMap.put(category.name(), stat);
            }

        }

        return resultMap;
    }
*/

    @Override
    public Map<String, ReportStatisticsResp> getReportStatistic(String taskId, String modelId) {
        List<TaskAnswerWithQuestionInfoVO> answerList = getMergedTaskAnswers(Long.valueOf(taskId), Long.valueOf(modelId));

        if (CollectionUtil.isEmpty(answerList)) {
            return Collections.emptyMap();
        }

        Map<String, QuestionTagInfoDO> tagInfoMap = questionTagInfoMapper.selectList().stream()
                .collect(Collectors.toMap(QuestionTagInfoDO::getTagId, tag -> tag));

        // 初始化统计容器
        Map<QuestionCategoryEnum, Map<QuestionDifficultyEnum, Long>> categoryCountMap = new HashMap<>();
        Map<QuestionCategoryEnum, Map<String, TagStatisticsResp>> categoryTagMap = new HashMap<>();
        Map<QuestionCategoryEnum, Map<String, Long>> categoryAttackMap = new HashMap<>();
        Map<QuestionCategoryEnum, Long> qualifiedCountMap = new HashMap<>();
        Map<QuestionCategoryEnum, Map<String, Integer>> tagQualifiedCountMap = new HashMap<>();

        // 遍历数据填充统计
        for (TaskAnswerWithQuestionInfoVO answer : answerList) {
            processSingleAnswer(answer, tagInfoMap,
                    categoryCountMap, categoryTagMap,
                    categoryAttackMap, qualifiedCountMap, tagQualifiedCountMap);
        }

        // 组装最终结果
        return buildReportStatisticsResult(
                categoryCountMap, categoryTagMap,
                categoryAttackMap, qualifiedCountMap,
                tagQualifiedCountMap
        );
    }

    /*
    @Override
    public Map<String, ReportStatisticsResp> getAttackCountStatistics(String taskId, String modelId) {
        List<TaskAnswerWithQuestionInfoVO> answerList = getMergedTaskAnswers(Long.valueOf(taskId), Long.valueOf(modelId));

        if (CollectionUtil.isEmpty(answerList)) {
            return Collections.emptyMap();
        }

        Map<String, QuestionTagInfoDO> tagInfoMap = questionTagInfoMapper.selectList().stream()
                .collect(Collectors.toMap(QuestionTagInfoDO::getTagId, tag -> tag));

        // 初始化统计容器
        Map<QuestionCategoryEnum, Map<QuestionDifficultyEnum, Long>> categoryCountMap = new HashMap<>();
        Map<QuestionCategoryEnum, Map<String, TagStatisticsResp>> categoryTagMap = new HashMap<>();
        Map<QuestionCategoryEnum, Map<String, Long>> categoryAttackMap = new HashMap<>();
        Map<QuestionCategoryEnum, Long> qualifiedCountMap = new HashMap<>();
        Map<QuestionCategoryEnum, Map<String, Integer>> tagQualifiedCountMap = new HashMap<>();

        // 遍历数据填充统计
        for (TaskAnswerWithQuestionInfoVO answer : answerList) {
            processSingleAnswer(answer, tagInfoMap,
                    categoryCountMap, categoryTagMap,
                    categoryAttackMap, qualifiedCountMap, tagQualifiedCountMap);
        }

        // 组装最终结果
        return buildReportStatisticsResult(
                categoryCountMap, categoryTagMap,
                categoryAttackMap, qualifiedCountMap,
                tagQualifiedCountMap
        );
    }
     */

    private void processSingleAnswer(TaskAnswerWithQuestionInfoVO answer,
                                     Map<String, QuestionTagInfoDO> tagInfoMap,
                                     Map<QuestionCategoryEnum, Map<QuestionDifficultyEnum, Long>> categoryCountMap,
                                     Map<QuestionCategoryEnum, Map<String, TagStatisticsResp>> categoryTagMap,
                                     Map<QuestionCategoryEnum, Map<String, Long>> categoryAttackMap,
                                     Map<QuestionCategoryEnum, Long> qualifiedCountMap,
                                     Map<QuestionCategoryEnum, Map<String, Integer>> tagQualifiedCountMap) {

        QuestionCategoryEnum category = QuestionCategoryEnum.valueOf(answer.getQuestionCategory());
        QuestionDifficultyEnum difficulty = QuestionDifficultyEnum.valueOf(answer.getDifficulty());

        // 难度统计
        categoryCountMap.computeIfAbsent(category, k -> new HashMap<>())
                .merge(difficulty, 1L, Long::sum);

        // 标签统计
        handleTagStatistics(answer.getTags(), answer.getJudgeResult(), category, tagInfoMap,
                categoryTagMap, tagQualifiedCountMap);

        // 攻击方式统计
        if (StrUtil.isNotBlank(answer.getAttackMethod())) {
            categoryAttackMap.computeIfAbsent(category, k -> new HashMap<>())
                    .merge(answer.getAttackMethod(), 1L, Long::sum);
        }

        // 合格题统计
        String qualified = JudgeResultEnum.getQualifiedStatus(answer.getJudgeResult(), category.name());
        if ("合格".equals(qualified)) {
            qualifiedCountMap.merge(category, 1L, Long::sum);
        }
    }


    private void handleTagStatistics(String tags, Integer judgeResult,
                                     QuestionCategoryEnum category,
                                     Map<String, QuestionTagInfoDO> tagInfoMap,
                                     Map<QuestionCategoryEnum, Map<String, TagStatisticsResp>> categoryTagMap,
                                     Map<QuestionCategoryEnum, Map<String, Integer>> tagQualifiedCountMap) {

        if (StrUtil.isBlank(tags)) return;

        String[] parts = tags.trim().split(",");
        String parentId;
        String childId = null;

        if (category == QuestionCategoryEnum.FORWARD) {
            parentId = parts[0].trim(); // 正向只有一级
        } else {
            if (parts.length != 2) return; // 负向必须有二级
            parentId = parts[0].trim();
            childId = parts[1].trim();
        }

        QuestionTagInfoDO parentTag = tagInfoMap.get(parentId);
        if (parentTag == null) return;
        String parentName = parentTag.getTagName();

        String childName = null;
        if (childId != null) {
            QuestionTagInfoDO childTag = tagInfoMap.get(childId);
            if (childTag == null) return;
            childName = childTag.getTagName();
        }

        Map<String, TagStatisticsResp> tagMap = categoryTagMap.computeIfAbsent(category, k -> new LinkedHashMap<>());

        // 一级标签 count，只加一次
        TagStatisticsResp parent = tagMap.computeIfAbsent(parentName, k -> {
            TagStatisticsResp tag = new TagStatisticsResp(parentName, 0);
            tag.setChildren(new ArrayList<>());
            return tag;
        });
        parent.setCount(parent.getCount() + 1); // ✅ 父标签 count 只加一次

        // 二级标签（仅负向）
        if (childName != null) {
            final String finalChildName = childName;
            TagStatisticsResp child = parent.getChildren().stream()
                    .filter(c -> c.getName().equals(finalChildName))
                    .findFirst()
                    .orElseGet(() -> {
                        TagStatisticsResp c = new TagStatisticsResp(finalChildName, 0);
                        parent.getChildren().add(c);
                        return c;
                    });
            child.setCount(child.getCount() + 1);
        }

        // 合格统计
        String qualified = JudgeResultEnum.getQualifiedStatus(judgeResult, category.name());
        if ("合格".equals(qualified)) {
            Map<String, Integer> qualifiedMap = tagQualifiedCountMap.computeIfAbsent(category, k -> new HashMap<>());
            if (childName != null) {
                qualifiedMap.merge(childName, 1, Integer::sum);
            } else {
                qualifiedMap.merge(parentName, 1, Integer::sum);
            }
        }
    }


    private Map<String, ReportStatisticsResp> buildReportStatisticsResult(
            Map<QuestionCategoryEnum, Map<QuestionDifficultyEnum, Long>> categoryCountMap,
            Map<QuestionCategoryEnum, Map<String, TagStatisticsResp>> categoryTagMap,
            Map<QuestionCategoryEnum, Map<String, Long>> categoryAttackMap,
            Map<QuestionCategoryEnum, Long> qualifiedCountMap,
            Map<QuestionCategoryEnum, Map<String, Integer>> tagQualifiedCountMap) {

        Map<String, ReportStatisticsResp> result = new HashMap<>();

        for (QuestionCategoryEnum category : categoryCountMap.keySet()) {
            ReportStatisticsResp stat = new ReportStatisticsResp();
            stat.setName(category.name());

            Map<QuestionDifficultyEnum, Long> difficultyMap = categoryCountMap.get(category);
            long total = difficultyMap.values().stream().mapToLong(Long::longValue).sum();
            long qualified = qualifiedCountMap.getOrDefault(category, 0L);

            stat.setTotal(total);
            stat.setHard(difficultyMap.getOrDefault(QuestionDifficultyEnum.HARD, 0L));
            stat.setMedium(difficultyMap.getOrDefault(QuestionDifficultyEnum.MEDIUM, 0L));
            stat.setSimple(difficultyMap.getOrDefault(QuestionDifficultyEnum.SIMPLE, 0L));

            double ratioTotal;
            if (category == QuestionCategoryEnum.FORWARD) { // 正向题：显示不合格率
                ratioTotal = total == 0 ? 0 : 1.0 - ((double) qualified / total);
            } else { // 负向题：显示合格率
                ratioTotal = total == 0 ? 0 : (double) qualified / total;
            }
            String per = String.format("%.2f%%", ratioTotal * 100);

            //String per = total == 0 ? "0%" : String.format("%.2f%%", 100.0 * qualified / total);
            boolean isQualified = qualifiedStandardConfiguration.isTotalQualified(category, (int) qualified, total);
            String resultStr = isQualified ? "合格" : "不合格";
            String standard = qualifiedStandardConfiguration.getTotalStandardString(category);

            stat.setPer(per);
            stat.setResult(resultStr);
            stat.setStandard(standard);

            // 标签合格率统计
            List<TagStatisticsResp> tagList = new ArrayList<>(categoryTagMap.getOrDefault(category, Collections.emptyMap()).values());
            Map<String, Integer> qualifiedTagMap = tagQualifiedCountMap.getOrDefault(category, Collections.emptyMap());

            for (TagStatisticsResp parentTag : tagList) {
                long parentTotal;
                int parentQualified;

                if (category == QuestionCategoryEnum.FORWARD) { // 正向题
                    parentTotal = parentTag.getCount();
                    parentQualified = qualifiedTagMap.getOrDefault(parentTag.getName(), 0);
                    double ratio = parentTotal == 0 ? 0 : 1.0 - ((double) parentQualified / parentTotal); // 显示不合格率
                    parentTag.setPer(String.format("%.2f%%", ratio * 100));
                } else { // 负向题
                    parentTotal = parentTag.getChildren().stream().mapToLong(TagStatisticsResp::getCount).sum();
                    parentQualified = parentTag.getChildren().stream()
                            .mapToInt(child -> qualifiedTagMap.getOrDefault(child.getName(), 0))
                            .sum();
                    double ratio = parentTotal == 0 ? 0 : (double) parentQualified / parentTotal;
                    parentTag.setPer(String.format("%.2f%%", ratio * 100));
                }
                String parentResult = qualifiedStandardConfiguration.isTagQualified(category, parentQualified, parentTotal) ? "合格" : "不合格";
                String parentStandard = qualifiedStandardConfiguration.getTagStandardString(category);

                parentTag.setResult(parentResult);
                parentTag.setStandard(parentStandard);

                for (TagStatisticsResp child : parentTag.getChildren()) {
                    long tagTotal = child.getCount();
                    int tagQualified = qualifiedTagMap.getOrDefault(child.getName(), 0);

                    double ratio = tagTotal == 0 ? 0 : (double) tagQualified / tagTotal;
                    if (category == QuestionCategoryEnum.FORWARD) {
                        ratio = 1 - ratio; // 正向题：显示不合格率
                    }
                    String tagPer = String.format("%.2f%%", ratio * 100);

                    String tagResult = qualifiedStandardConfiguration.isTagQualified(category, tagQualified, tagTotal) ? "合格" : "不合格";
                    String tagStandard = qualifiedStandardConfiguration.getTagStandardString(category);

                    child.setPer(tagPer);
                    child.setResult(tagResult);
                    child.setStandard(tagStandard);
                }
            }

            stat.setTagStatistics(tagList);
            stat.setAttackMethodStatistics(categoryAttackMap.getOrDefault(category, Collections.emptyMap()));

            result.put(category.name(), stat);
        }

        return result;
    }


    public List<TagSummaryResp> getReportTagSummary(String taskId, String modelId) {
        List<TaskAnswerWithQuestionInfoVO> answerList = getMergedTaskAnswers(Long.valueOf(taskId), Long.valueOf(modelId));
        if (CollectionUtil.isEmpty(answerList)) {
            return Collections.emptyList();
        }

        Map<String, QuestionTagInfoDO> tagInfoMap = questionTagInfoMapper.selectList()
                .stream()
                .collect(Collectors.toMap(QuestionTagInfoDO::getTagId, tag -> tag));

        // 用于统计一级标签（parentTag）
        Map<String, TagSummaryResp.TagChildResp> negativeParentMap = new LinkedHashMap<>();
        Map<String, TagSummaryResp.TagChildResp> positiveParentMap = new LinkedHashMap<>();

        long negativeCount = 0L;
        long positiveCount = 0L;

        for (TaskAnswerWithQuestionInfoVO answer : answerList) {
            String tags = answer.getTags();
            if (StrUtil.isBlank(tags)) continue;
            String[] parts = tags.trim().split(",");
            //if (parts.length != 2) continue;

            String parentId = parts[0].trim();
            // String childId = parts[1].trim();  // 不再统计子标签

            QuestionTagInfoDO parentTag = tagInfoMap.get(parentId);
            if (parentTag == null) continue;

            boolean isPositive = QuestionCategoryEnum.FORWARD.getCode().equalsIgnoreCase(answer.getQuestionCategory());
            Map<String, TagSummaryResp.TagChildResp> targetMap = isPositive ? positiveParentMap : negativeParentMap;

            TagSummaryResp.TagChildResp parentChild = targetMap.get(parentTag.getTagName());
            if (parentChild == null) {
                parentChild = new TagSummaryResp.TagChildResp();
                parentChild.setName(parentTag.getTagName());
                parentChild.setCount(0L);
                parentChild.setType("文本");  // 一级标签type 仍用“文本”
                targetMap.put(parentTag.getTagName(), parentChild);
            }

            parentChild.setCount(parentChild.getCount() + 1);

            if (isPositive) positiveCount++;
            else negativeCount++;
        }

        TagSummaryResp.TagChildResp summaryChild = new TagSummaryResp.TagChildResp();
        summaryChild.setName("");
        summaryChild.setType("");
        summaryChild.setCount(negativeCount + positiveCount);

        TagSummaryResp negativeResp = new TagSummaryResp();
        negativeResp.setName("生成内容安全评估");
        negativeResp.setCount(negativeCount);
        negativeResp.setChildren(new ArrayList<>(negativeParentMap.values()));

        TagSummaryResp positiveResp = new TagSummaryResp();
        positiveResp.setName("非拒答评估");
        positiveResp.setCount(positiveCount);
        positiveResp.setChildren(new ArrayList<>(positiveParentMap.values()));

        TagSummaryResp summaryResp = new TagSummaryResp();
        summaryResp.setName("统计");
        summaryResp.setCount(negativeCount + positiveCount);
        summaryResp.setChildren(Collections.singletonList(summaryChild));

        return Arrays.asList(negativeResp, positiveResp, summaryResp);
    }


    @Override
    public Map<String, List<ReportPerStatisticResp>> getReportAttackMethod(String taskId, String modelId) {
        List<TaskAnswerWithQuestionInfoVO> answerList = getMergedTaskAnswers(Long.valueOf(taskId), Long.valueOf(modelId));
        if (CollectionUtil.isEmpty(answerList)) {
            return Collections.emptyMap();
        }

        // Map<FORWARD/NEGATIVE, Map<攻击方式, 总数>>
        Map<String, Map<String, Long>> totalMap = new HashMap<>();
        // Map<FORWARD/NEGATIVE, Map<攻击方式, 合格数>>
        Map<String, Map<String, Long>> qualifiedMap = new HashMap<>();

        for (TaskAnswerWithQuestionInfoVO answer : answerList) {
            String category = answer.getQuestionCategory(); // FORWARD / NEGATIVE
            String attackMethod = answer.getAttackMethod();

            if (StrUtil.isBlank(category) || StrUtil.isBlank(attackMethod)) {
                continue;
            }

            // 统计总数
            totalMap.computeIfAbsent(category, k -> new HashMap<>())
                    .merge(attackMethod, 1L, Long::sum);

            // 判断是否合格
            String qualifiedStatus = JudgeResultEnum.getQualifiedStatus(answer.getJudgeResult(), category);
            if ("合格".equals(qualifiedStatus)) {
                qualifiedMap.computeIfAbsent(category, k -> new HashMap<>())
                        .merge(attackMethod, 1L, Long::sum);
            }
        }

        Map<String, List<ReportPerStatisticResp>> result = new HashMap<>();

        for (String category : totalMap.keySet()) {
            Map<String, Long> attackTotalMap = totalMap.get(category);
            Map<String, Long> attackQualifiedMap = qualifiedMap.getOrDefault(category, Collections.emptyMap());

            List<ReportPerStatisticResp> attackStats = new ArrayList<>();

            for (String attackMethod : attackTotalMap.keySet()) {
                long total = attackTotalMap.getOrDefault(attackMethod, 0L);
                long qualified = attackQualifiedMap.getOrDefault(attackMethod, 0L);
                //double per = total == 0 ? 0.0 : (100.0 * qualified / total);
                double per = total == 0 ? 0.0 : Math.round(100.0 * qualified / total * 100.0) / 100.0;

                ReportPerStatisticResp resp = new ReportPerStatisticResp();
                resp.setName(attackMethod);
                resp.setTotal(total);
                resp.setQualified(qualified);
                resp.setPer(per);

                attackStats.add(resp);
            }

            result.put(category, attackStats);
        }

        return result;
    }



    public ReportBaseInfoResp getReportBaseData(String taskId, String modelId) {
        JobResp jobInfo = jobService.getJob(Long.valueOf(taskId));
        if (jobInfo != null && jobInfo.getModelInfoMap() != null) {
            ModelInfoDO model = jobInfo.getModelInfoMap().get(Long.valueOf(modelId));
            if (model != null) {
                ReportBaseInfoResp resp = new ReportBaseInfoResp();
                resp.setTarget(model.getModelName() + " " + model.getModelVersion());
                resp.setTaskName(jobInfo.getName());
                resp.setStartTime(jobInfo.getStartTime());
                resp.setEndTime(jobInfo.getEndTime());
                return resp;
            }
        }
        return null;
    }

    /*
    public List<TaskAnswerWithQuestionInfoVO> getMergedTaskAnswers(Long taskId, Long modelId) {
        List<TaskAnswerDO> taskAnswers = taskAnswerMapper.queryAllTaskAnswers(taskId, modelId);

        if (CollectionUtils.isEmpty(taskAnswers)) {
            return Collections.emptyList();
        }

        List<QuestionDO> questions = getQuestionsByIdAndVersion(taskAnswers);

        // 构建 map 便于合并
        Map<String, QuestionDO> questionMap = questions.stream().collect(Collectors.toMap(
                q -> q.getQuestionId() + "#" + q.getVersion(),
                q -> q
        ));

        // 合并信息
        List<TaskAnswerWithQuestionInfoVO> result = new ArrayList<>();
        for (TaskAnswerDO taskAnswer : taskAnswers) {
            TaskAnswerWithQuestionInfoVO vo = new TaskAnswerWithQuestionInfoVO();

            // 拷贝 TaskAnswerDO 字段
            BeanUtils.copyProperties(taskAnswer, vo);

            // 拼接 key 查询 QuestionDO
            String key = taskAnswer.getQuestionId() + "#" + taskAnswer.getQuestionVersion();
            QuestionDO question = questionMap.get(key);
            if (question != null) {
                vo.setAttackMethod(question.getAttackMethod());
                vo.setTags(question.getTags());
                vo.setDifficulty(question.getDifficulty());
                vo.setDataSource(question.getDataSource());
                vo.setQuestionDesc(question.getDesc());
            }

            result.add(vo);
        }

        return result;
    }
*/

    public List<TaskAnswerWithQuestionInfoVO> getMergedTaskAnswers(Long taskId, Long modelId) {
        int pageNo = 1;
        int pageSize = 100;  // 每次查询多少条，根据业务调整
        List<TaskAnswerDO> allTaskAnswers = new ArrayList<>();

        while (true) {
            int offset = (pageNo - 1) * pageSize;
            List<TaskAnswerDO> pageData = taskAnswerMapper.queryTaskAnswersByPage(taskId, modelId, offset, pageSize);

            if (CollectionUtils.isEmpty(pageData)) {
                break;
            }

            allTaskAnswers.addAll(pageData);
            if (pageData.size() < pageSize) {
                // 最后一页，数据不足pageSize条，结束循环
                break;
            }
            pageNo++;
        }

        if (CollectionUtils.isEmpty(allTaskAnswers)) {
            return Collections.emptyList();
        }

        List<QuestionDO> questions = getQuestionsByIdAndVersion(allTaskAnswers);
        Map<String, QuestionDO> questionMap = questions.stream().collect(Collectors.toMap(
                q -> q.getQuestionId() + "#" + q.getVersion(),
                q -> q
        ));

        List<TaskAnswerWithQuestionInfoVO> result = new ArrayList<>();
        for (TaskAnswerDO taskAnswer : allTaskAnswers) {
            if (taskAnswer.getJudgeResult() == 0 || taskAnswer.getJudgeResult() == 1) {
                continue;
            }
            TaskAnswerWithQuestionInfoVO vo = new TaskAnswerWithQuestionInfoVO();
            BeanUtils.copyProperties(taskAnswer, vo);

            String key = taskAnswer.getQuestionId() + "#" + taskAnswer.getQuestionVersion();
            QuestionDO question = questionMap.get(key);
            if (question != null) {
                vo.setAttackMethod(question.getAttackMethod());
                vo.setTags(question.getTags());
                vo.setDifficulty(question.getDifficulty());
                vo.setDataSource(question.getDataSource());
                vo.setQuestionDesc(question.getDesc());
            }

            result.add(vo);
        }

        return result;
    }


    @Override
    public Map<String, List<ReportPerStatisticResp>> getReportTagStatistic(String taskId, String modelId) {
        List<TaskAnswerWithQuestionInfoVO> answerList = getMergedTaskAnswers(Long.valueOf(taskId), Long.valueOf(modelId));
        if (CollectionUtil.isEmpty(answerList)) {
            return Collections.emptyMap();
        }

        // 标签元信息
        Map<String, QuestionTagInfoDO> tagInfoMap = questionTagInfoMapper.selectList().stream()
                .collect(Collectors.toMap(QuestionTagInfoDO::getTagId, tag -> tag));

        // Map<FORWARD/NEGATIVE, Map<childTagName, 总数>>
        Map<String, Map<String, Long>> totalMap = new HashMap<>();
        // Map<FORWARD/NEGATIVE, Map<childTagName, 合格数>>
        Map<String, Map<String, Long>> qualifiedMap = new HashMap<>();

        for (TaskAnswerWithQuestionInfoVO answer : answerList) {
            String category = answer.getQuestionCategory(); // FORWARD / NEGATIVE
            String tags = answer.getTags();

            if (StrUtil.isBlank(category) || StrUtil.isBlank(tags)) {
                continue;
            }

            String[] parts = tags.split(",");
            String tagId;

            if (QuestionCategoryEnum.FORWARD.getCode().equalsIgnoreCase(category)) {
                // ✅ 正向：只有一级标签
                tagId = parts[0].trim();
            } else {
                // ✅ 负向：需要 parentId,childId，取二级标签
                if (parts.length != 2) {
                    continue; // 格式不对，跳过
                }
                tagId = parts[1].trim();
            }

            QuestionTagInfoDO tag = tagInfoMap.get(tagId);
            if (tag == null) {
                continue;
            }

            String tagName = tag.getTagName();

            // 总数统计
            totalMap.computeIfAbsent(category, k -> new HashMap<>())
                    .merge(tagName, 1L, Long::sum);

            // 合格数统计
            String qualifiedStatus = JudgeResultEnum.getQualifiedStatus(answer.getJudgeResult(), category);
            if ("合格".equals(qualifiedStatus)) {
                qualifiedMap.computeIfAbsent(category, k -> new HashMap<>())
                        .merge(tagName, 1L, Long::sum);
            }
        }

        Map<String, List<ReportPerStatisticResp>> result = new HashMap<>();

        for (String category : totalMap.keySet()) {
            Map<String, Long> tagTotalMap = totalMap.get(category);
            Map<String, Long> tagQualifiedMap = qualifiedMap.getOrDefault(category, Collections.emptyMap());

            List<ReportPerStatisticResp> tagStats = new ArrayList<>();

            for (String tagName : tagTotalMap.keySet()) {
                long total = tagTotalMap.getOrDefault(tagName, 0L);
                long qualified = tagQualifiedMap.getOrDefault(tagName, 0L);
                double per = total == 0 ? 0.0 : Math.round(100.0 * qualified / total * 100.0) / 100.0;

                ReportPerStatisticResp resp = new ReportPerStatisticResp();
                resp.setName(tagName);
                resp.setTotal(total);
                resp.setQualified(qualified);
                resp.setPer(per);

                tagStats.add(resp);
            }

            result.put(category, tagStats);
        }

        return result;
    }


    public List<QuestionDO> getQuestionsByIdAndVersion(List<TaskAnswerDO> taskAnswers) {
        // 1. 组装 questionId + version 对
        List<QuestionKey> keyList = taskAnswers.stream()
                .map(ans -> new QuestionKey(ans.getQuestionId(), ans.getQuestionVersion()))
                .collect(Collectors.toList());

        if (keyList.isEmpty()) {
            return Collections.emptyList();
        }

        // 2. 获取所有 questionId（String 转 Long）
        Set<Long> questionIdSet = keyList.stream()
                .map(key -> Long.valueOf(key.getQuestionId()))
                .collect(Collectors.toSet());

        // 3. 查询所有题目的最新版本号
        Map<String, Integer> latestVersionMap = questionMapper.selectLatestVersions(questionIdSet)
                .stream()
                .collect(Collectors.toMap(QuestionDO::getQuestionId, QuestionDO::getVersion));

        // 4. 拆分 keyList 成两个集合：最新题目 和 快照题目
        List<QuestionKey> latestKeys = new ArrayList<>();
        List<QuestionKey> snapshotKeys = new ArrayList<>();
        for (QuestionKey key : keyList) {
            String qid = key.getQuestionId();
            Integer latest = latestVersionMap.get(qid);
            if (latest != null && latest.equals(key.getVersion())) {
                latestKeys.add(key);
            } else {
                snapshotKeys.add(key);
            }
        }

        // 5. 分别查询 question_info 和 question_snapshot 表
        List<QuestionDO> result = new ArrayList<>();
        if (!latestKeys.isEmpty()) {
            result.addAll(questionMapper.selectLatestQuestionBatch(latestKeys));
        }
        if (!snapshotKeys.isEmpty()) {
            result.addAll(questionMapper.selectSnapshotQuestionBatch(snapshotKeys));
        }

        return result;
    }

    public List<TaskAnswerAbnormalResp> listAbnormalTaskAnswers(String modelId, String taskId, String category, int count) {
        List<TaskAnswerDO> taskAnswers = taskAnswerMapper.listTaskAnswerByAbnormalViolationLimitCount(Long.valueOf(taskId), Long.valueOf(modelId), category, count);
        if (CollectionUtil.isEmpty(taskAnswers)) {
            return Collections.emptyList();
        }

        List<QuestionDO> questionList = getQuestionsByIdAndVersion(taskAnswers);
        Map<String, QuestionDO> questionMap = questionList.stream()
                .collect(Collectors.toMap(QuestionDO::getQuestionId, Function.identity()));

        // 标签映射
        Map<String, QuestionTagInfoDO> tagInfoMap = questionTagInfoMapper.selectList()
                .stream()
                .collect(Collectors.toMap(QuestionTagInfoDO::getTagId, tag -> tag));

        List<TaskAnswerAbnormalResp> abnormalRespList = new ArrayList<>();
        for (TaskAnswerDO answer : taskAnswers) {
            TaskAnswerAbnormalResp resp = new TaskAnswerAbnormalResp();
            resp.setModelId(answer.getModelId());
            resp.setModelName(answer.getModelName());
            resp.setModelVersion(answer.getModelVersion());

            QuestionDO question = questionMap.get(answer.getQuestionId());
            resp.setQuestionContent(question != null ? answer.getQuestionContent() : "");

            resp.setAnswerContent(answer.getAnswerContent());
            resp.setAppName(answer.getAppName());
            resp.setJudgeResult(answer.getJudgeResult());
            resp.setViolation(answer.getViolation());
            resp.setThinkProcess(answer.getThinkProcess());

            if (StrUtil.isNotBlank(question.getTags())) {
                String[] tags = question.getTags().split(",");
                if (tags.length >= 1) {
                    String firstTagId = tags[0].trim();
                    QuestionTagInfoDO firstTag = tagInfoMap.get(firstTagId);
                    resp.setFirstTag(firstTag != null ? firstTag.getTagName() : firstTagId);
                }
                if (tags.length >= 2) {
                    String secondTagId = tags[1].trim();
                    QuestionTagInfoDO secondTag = tagInfoMap.get(secondTagId);
                    resp.setSecondTag(secondTag != null ? secondTag.getTagName() : secondTagId);
                }
            }

            resp.setQuestionCategory(answer.getQuestionCategory());

            abnormalRespList.add(resp);
        }

        return abnormalRespList;
    }

    public PageResult<TaskAnswerAbnormalResp> pageAbnormalTaskAnswers(
            String modelId, String taskId, String category, int page, int size) {

        // 直接调用已有分页方法
        Page<TaskAnswerAbnormalResp> resultPage = taskAnswerService.pageQueryTaskAnswerByAbnormalViolation(
                page, size, Long.valueOf(taskId), Long.valueOf(modelId), category
        );

        // 构建并返回 PageResult
        return new PageResult<>(resultPage.getRecords(), resultPage.getTotal());
    }



    /**
     * 读取PDF文件并转换为字节数组
     *
     * @param filePath PDF文件路径
     * @return 文件的字节数组
     */
    private byte[] readPdfFileAsByteArray(String filePath) {
        File file = new File(filePath);
        if (!file.exists()) {
            throw new RuntimeException("报告文件不存在");
        }

        try (FileInputStream fis = new FileInputStream(file); ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[1024];
            int length;
            while ((length = fis.read(buffer)) != -1) {
                baos.write(buffer, 0, length);
            }
            return baos.toByteArray();
        } catch (IOException e) {
            log.error("读取报告文件失败:{}", e.getMessage());
            throw new RuntimeException("读取报告文件失败");
        }
    }

    /**
     * 生成PDF节点
     */
    private void generateDocument(Document document,
                                  JobResp jobInfo,
                                  ModelInfoDO modelInfoDO,
                                  EvaluateResultStatisticsGroupResp statistics,
                                  QuestionSetInfoDO questionSetInfo,
                                  List<QuestionSetItemStatisticsResp> questionSetStatistics,
                                  Map<String, List<TaskAnswerAbnormalResp>> taskAnswerByAbnormalViolation) throws DocumentException, IOException {
        // 1.基本评测信息组装
        generateBasicInfo(document, jobInfo, questionSetInfo, questionSetStatistics);
        // 2.评测指标总览组装
        overviewEvaluationIndicators(document, jobInfo, modelInfoDO, statistics);
        // 3.评测指标详情
//        detailsEvaluationIndicators(document, jobInfo, modelInfoDO, statistics);
        // 4.评测详情
        reviewDetailsDisplay(document, taskAnswerByAbnormalViolation);
    }


    /**
     * 1.基本评测信息组装
     */
    private static void generateBasicInfo(Document document,
                                          JobResp jobInfo,
                                          QuestionSetInfoDO questionSetInfo,
                                          List<QuestionSetItemStatisticsResp> questionSetStatistics) throws DocumentException, IOException {
        // 添加引用标题
        createVerticalLineChunk(document, 1, "基本测评信息");
        // 添加一个空行
        document.add(Chunk.NEWLINE);
        // 添加基本评测信息表格
        document.add(createBasicPdfTable(jobInfo, questionSetInfo, questionSetStatistics));
        // 添加一个空行
        document.add(Chunk.NEWLINE);
    }

    /**
     * 1.2 基本评测信息-Table表单
     */
    private static PdfPTable createBasicPdfTable(JobResp jobInfo,
                                                 QuestionSetInfoDO questionSetInfo,
                                                 List<QuestionSetItemStatisticsResp> questionSetStatistics) throws DocumentException, IOException {
        Map<String, QuestionSetItemStatisticsResp> statisticsRespMap = questionSetStatistics.stream().collect(Collectors.toMap(QuestionSetItemStatisticsResp::getName, resp -> resp));

        // 创建一个 4 列的表格
        PdfPTable table = new PdfPTable(4);
        BaseFont baseFont = BaseFont.createFont("STSong-Light", "UniGB-UCS2-H", BaseFont.NOT_EMBEDDED);
        Font chineseFont = new Font(baseFont, 12, Font.NORMAL, BaseColor.BLACK); // 黑色字体

        // 设置表格宽度（可选）
        table.setWidthPercentage(100); // 占页面宽度的 100%
        // 添加第一行
        for (int i = 0; i < 2; i++) {
            PdfPCell pdfPCell = new PdfPCell();
            // 字体左右居中
            pdfPCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            // 字体上下居中
            pdfPCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            pdfPCell.setFixedHeight(CELL_HEIGHT);
            pdfPCell.setBorderColor(DEFAULT_THEME_COLOR);
            if (i == 0) {
                pdfPCell.setPhrase(new com.itextpdf.text.Phrase("评测目标", chineseFont));
            } else {
                pdfPCell.setColspan(3);
                pdfPCell.setPhrase(new com.itextpdf.text.Phrase(questionSetInfo.getQuestionSetName(), chineseFont));
            }
            table.addCell(pdfPCell);
        }

        // 添加第二行
        for (int i = 0; i < 2; i++) {
            PdfPCell pdfPCell = new PdfPCell();
            // 字体左右居中
            pdfPCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            // 字体上下居中
            pdfPCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            pdfPCell.setFixedHeight(CELL_HEIGHT);
            pdfPCell.setBorderColor(DEFAULT_THEME_COLOR);
            if (i == 0) {
                pdfPCell.setPhrase(new com.itextpdf.text.Phrase("评测任务名称", chineseFont));
            } else {
                pdfPCell.setColspan(3);
                pdfPCell.setPhrase(new com.itextpdf.text.Phrase(jobInfo.getName(), chineseFont));
                pdfPCell.setBackgroundColor(BACKGROUND_COLOR);
            }
            table.addCell(pdfPCell);
        }

        // 添加第三行
        PdfPCell header3 = new PdfPCell();
        header3.setFixedHeight(CELL_HEIGHT);
        // 跨2行
        header3.setRowspan(2);
        // 字体左右居中
        header3.setHorizontalAlignment(Element.ALIGN_CENTER);
        // 字体上下居中
        header3.setVerticalAlignment(Element.ALIGN_MIDDLE);
        header3.setBorderColor(DEFAULT_THEME_COLOR);
        header3.setPhrase(new com.itextpdf.text.Phrase("题目分布", chineseFont));
        table.addCell(header3);

        // 第三行-正向
        PdfPCell pdfPCellForward = new PdfPCell();
        pdfPCellForward.setFixedHeight(CELL_HEIGHT);
        pdfPCellForward.setColspan(3);
        // 字体左右居中
        pdfPCellForward.setHorizontalAlignment(Element.ALIGN_CENTER);
        // 字体上下居中
        pdfPCellForward.setVerticalAlignment(Element.ALIGN_MIDDLE);
        pdfPCellForward.setBorderColor(DEFAULT_THEME_COLOR);
        QuestionSetItemStatisticsResp forwardStatistic = statisticsRespMap.get(QuestionCategoryEnum.FORWARD.name());
        pdfPCellForward.setPhrase(new com.itextpdf.text.Phrase(ObjectUtil.isNull(forwardStatistic) ? "-" : String.format("正向题库[简单:%s  中等:%s  困难: %s]",
                ObjectUtil.defaultIfNull(forwardStatistic.getSimple(), 0),
                ObjectUtil.defaultIfNull(forwardStatistic.getMedium(), 0),
                ObjectUtil.defaultIfNull(forwardStatistic.getHard(), 0)
        ),
                chineseFont));
        table.addCell(pdfPCellForward);
        // 第三行-负向
        PdfPCell pdfPCellNegative = new PdfPCell();
        pdfPCellNegative.setFixedHeight(CELL_HEIGHT);
        pdfPCellNegative.setColspan(3);
        // 字体左右居中
        pdfPCellNegative.setHorizontalAlignment(Element.ALIGN_CENTER);
        // 字体上下居中
        pdfPCellNegative.setVerticalAlignment(Element.ALIGN_MIDDLE);
        pdfPCellNegative.setBorderColor(DEFAULT_THEME_COLOR);
        pdfPCellNegative.setBackgroundColor(BACKGROUND_COLOR);
        QuestionSetItemStatisticsResp negativeStatistic = statisticsRespMap.get(QuestionCategoryEnum.NEGATIVE.name());
        pdfPCellNegative.setPhrase(new com.itextpdf.text.Phrase(ObjectUtil.isNull(negativeStatistic) ? "-" : String.format("负向题库[简单:%s  中等:%s  困难: %s]",
                ObjectUtil.defaultIfNull(negativeStatistic.getSimple(), 0),
                ObjectUtil.defaultIfNull(negativeStatistic.getMedium(), 0),
                ObjectUtil.defaultIfNull(negativeStatistic.getHard(), 0)
        ),
                chineseFont));
        table.addCell(pdfPCellNegative);


        // 添加第五行
        for (int i = 0; i < 4; i++) {
            PdfPCell pdfPCell = new PdfPCell();
            pdfPCell.setFixedHeight(CELL_HEIGHT);
            // 字体左右居中
            pdfPCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            // 字体上下居中
            pdfPCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            pdfPCell.setBorderColor(DEFAULT_THEME_COLOR);
            if (i % 2 == 0) {
                pdfPCell.setPhrase(new com.itextpdf.text.Phrase(i == 0 ? "任务开始时间" : "任务结束时间", chineseFont));
            } else {
                pdfPCell.setPhrase(new com.itextpdf.text.Phrase(i == 1 ? DateUtils.covertFormat(jobInfo.getStartTime()) : DateUtils.covertFormat(jobInfo.getEndTime()), chineseFont));
            }
            table.addCell(pdfPCell);
        }
        return table;
    }

    /**
     * 2.1 评测指标总览
     */
    private static void overviewEvaluationIndicators(Document document,
                                                     JobResp jobInfo,
                                                     ModelInfoDO modelInfoDO,
                                                     EvaluateResultStatisticsGroupResp statistics) throws DocumentException, IOException {
        // 添加引用标题
        createVerticalLineChunk(document, 1, "评测结果");
        // 添加一个空行
        document.add(Chunk.NEWLINE);
        createVerticalLineChunk(document, 2, "正向评测结果");
        createManualForwardEvaluationPdfRingPie(document, jobInfo, modelInfoDO, statistics);

        document.newPage();
        // 添加一个空行
        document.add(Chunk.NEWLINE);
        createVerticalLineChunk(document, 2, "负向评测结果");
        createManualNegativeEvaluationPdfRingPie(document, jobInfo, modelInfoDO, statistics);
    }


    /**
     * 3.1 评测指标详情
     */
    private static void detailsEvaluationIndicators(Document document,
                                                    JobResp jobInfo,
                                                    ModelInfoDO modelInfoDO,
                                                    EvaluateResultStatisticsGroupResp statistics) throws DocumentException, IOException {
        // 添加引用标题
        createVerticalLineChunk(document, 1, "评测指标详情");
        createVerticalLineChunk(document, 2, "样本信息");
        // 添加一个空行
        document.add(Chunk.NEWLINE);
        // 添加基本评测信息表格
        document.add(createDetailsPdfTable());
        // 添加一个空行
        document.add(Chunk.NEWLINE);
        document.add(createDetailsItemPdfTable());
    }

    /**
     *    4.1 评测详情
     */
    private static void reviewDetailsDisplay(Document document,
                                             Map<String, List<TaskAnswerAbnormalResp>> taskAnswerByAbnormalViolation) throws DocumentException, IOException {
        // 添加引用标题
        createVerticalLineChunk(document, 1, "评测详情");
        // 构建正向题库异常题目信息
        createForwardReviewDetailsDisplay(document, taskAnswerByAbnormalViolation);
        // 构建负向题库异常题目信息
        createNegativeReviewDetailsDisplay(document, taskAnswerByAbnormalViolation);
    }

    /**
     * 创建引用标题
     */
    private static void createVerticalLineChunk(Document document,
                                                int level,
                                                String title) throws DocumentException, IOException {
        // 创建引用标题
        BaseFont baseFont = BaseFont.createFont("STSong-Light", "UniGB-UCS2-H", BaseFont.NOT_EMBEDDED);
        Font titleFont = new Font(baseFont, 16, Font.BOLD, DEFAULT_THEME_COLOR); // 默认颜色粗字体
        Paragraph quoteTitle = new Paragraph(title, titleFont);
        quoteTitle.setIndentationLeft(20 * level);

        // 创建引用标题竖线
        Chunk firsetChunk = new Chunk(new VerticalPositionMark() {
            @Override
            public void draw(PdfContentByte canvas, float llx, float lly, float urx, float ury, float y) {
                // 根据等级设置竖线样式
                float lineWidth = getLineWidthByLevel(level); // 竖线宽度
                float marginLeft = 36; // 页面左边距
                float lineXOffset = level * 10; // 竖线偏移量
                float lineYOffset = -1 * 10; // 动态调整竖线起点的 Y 偏移量
                // 计算竖线起点和终点
                float lineX = marginLeft + lineXOffset; // 竖线 X 坐标
                float lineYStart = y + lineYOffset; // 竖线起点 Y 坐标
                float lineYEnd = lineYStart - 14; // 竖线终点 Y 坐标（高度为 14px）
                // 绘制竖线
                canvas.setLineWidth(lineWidth);
                canvas.setColorStroke(DEFAULT_THEME_COLOR);
                canvas.moveTo(lineX, lineYStart);
                canvas.lineTo(lineX, lineYEnd);
                canvas.stroke();
            }
        });
        document.add(firsetChunk);
        // 添加引用标题到文档
        document.add(quoteTitle);
    }

    /**
     * 根据等级获取竖线宽度
     *
     * @param level 等级
     * @return 竖线宽度
     */
    private static float getLineWidthByLevel(int level) {
        switch (level) {
            case 1:
                return 4f;
            case 2:
                return 3f;
            case 3:
                return 2f;
            default:
                throw new IllegalArgumentException("暂不支持的标题等级：" + level);
        }
    }

    /**
     * 创建评测指标总览环形图-自动评审-正向
     * @param document document
     */
    private static void createManualForwardEvaluationPdfRingPie(Document document,
                                                                JobResp jobInfo,
                                                                ModelInfoDO modelInfoDO,
                                                                EvaluateResultStatisticsGroupResp statistics) throws DocumentException, IOException {
        DefaultPieDataset dataset = new DefaultPieDataset();
        // 为了效果图兜底一个策略
        if (statistics.getManual().getAnswered() == 0
                && statistics.getManual().getUnanswered() == 0
                && statistics.getManual().getGeneratedInappropriateContent() == 0
                && statistics.getManual().getUncertainForward() == 0) {
            dataset.setValue("正常回答", 0.001);
            dataset.setValue("拒绝回答", 0.001);
            dataset.setValue("生成违规内容", 0.001);
            dataset.setValue("无法判断", 0.001);
        } else {
            dataset.setValue("正常回答", statistics.getManual().getAnswered());
            dataset.setValue("拒绝回答", statistics.getManual().getUnanswered());
            dataset.setValue("生成违规内容", statistics.getManual().getGeneratedInappropriateContent());
            dataset.setValue("无法判断", statistics.getManual().getUncertainForward());
        }


        // 创建自定义环形图
        double rejectRate = Double.parseDouble(String.valueOf(statistics.getManual().getNonRejected()));
        String formatted = String.format("拒答率：%.2f%%", rejectRate * 100);
        CustomRingPlot plot = new CustomRingPlot(dataset, formatted);
        // 设置环形图样式
        plot.setSectionDepth(0.4); // 环的厚度
        plot.setCircular(true);     // 确保是正圆形
        // 画布设置
        plot.setBackgroundPaint(Color.WHITE);
        // 设置plot边框颜色
        plot.setOutlinePaint(Color.WHITE);
        // 设置环形图样式
        // 扇形图管理
        plot.setSectionPaint("正常回答", ReportAnswerColorEnum.NORMAL_ANSWER.getColor());
        plot.setSectionPaint("拒绝回答", ReportAnswerColorEnum.REFUSE_ANSWER.getColor());
        plot.setSectionPaint("生成违规内容", ReportAnswerColorEnum.VIOLATION_CONTENT.getColor());
        plot.setSectionPaint("无法判断", ReportAnswerColorEnum.UNKNOWN.getColor());
        // 自定义标签管理
        plot.setLabelGenerator(new CustomLabelGenerator());

        // 创建图表
        JFreeChart chart = new JFreeChart(
                null,
                JFreeChart.DEFAULT_TITLE_FONT,
                plot,
                true               // 显示图例
        );
        // 设置抗锯齿
        chart.setAntiAlias(true);
        chart.setTextAntiAlias(true);
        // 设置图例文字大小和居中
        chart.getLegend().setItemFont(new java.awt.Font("黑体", java.awt.Font.BOLD, 25));
        chart.getLegend().setHorizontalAlignment(HorizontalAlignment.CENTER);
        chart.setBackgroundPaint(Color.WHITE);

        try {
            Image chartImage = Image.getInstance(createChartImageBytes(chart));
            chartImage.scaleToFit(400, 200);

            BaseFont baseFont = BaseFont.createFont("STSong-Light", "UniGB-UCS2-H", BaseFont.NOT_EMBEDDED);
            Font titleFont = new Font(baseFont, 10, Font.NORMAL, BaseColor.GRAY); // 灰色粗体字体
            Paragraph quoteTitle = new Paragraph(String.format("正向样本已审核数量：%s/正向样本数量：%s", statistics.getManual().getTotalForward() - statistics.getManual().getUnjudgedForward(), statistics.getManual().getTotalForward()), titleFont);
            quoteTitle.setIndentationLeft(90);
            document.add(quoteTitle);
            document.add(chartImage);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 创建评测指标总览环形图-自动评审-负向
     * @param document document
     */
    private static void createManualNegativeEvaluationPdfRingPie(Document document,
                                                                 JobResp jobInfo,
                                                                 ModelInfoDO modelInfoDO,
                                                                 EvaluateResultStatisticsGroupResp statistics) throws DocumentException, IOException {
        DefaultPieDataset dataset = new DefaultPieDataset();
        // 为了效果图兜底一个策略
        if (statistics.getManual().getPositiveGuidance() == 0
                && statistics.getManual().getRejected() == 0
                && statistics.getManual().getNotRejected() == 0
                && statistics.getManual().getUncertainNegative() == 0) {
            dataset.setValue("正向引导", 0.001);
            dataset.setValue("拒绝回答", 0.001);
            dataset.setValue("生成违规内容", 0.001);
            dataset.setValue("无法判断", 0.001);
        } else {
            dataset.setValue("正向引导", statistics.getManual().getPositiveGuidance());
            dataset.setValue("拒绝回答", statistics.getManual().getRejected());
            dataset.setValue("生成违规内容", statistics.getManual().getNotRejected());
            dataset.setValue("无法判断", statistics.getManual().getUncertainNegative());
        }


        // 创建自定义环形图
        double rejectRate = Double.parseDouble(String.valueOf(statistics.getManual().getShouldReject()));
        String formatted = String.format("拒答率：%.2f%%", rejectRate * 100);
        CustomRingPlot plot = new CustomRingPlot(dataset, formatted);
        // 设置环形图样式
        plot.setSectionDepth(0.4); // 环的厚度
        plot.setCircular(true);     // 确保是正圆形
        // 画布设置
        plot.setBackgroundPaint(Color.WHITE);
        // 设置plot边框颜色
        plot.setOutlinePaint(Color.WHITE);
        // 设置环形图样式
        // 扇形图管理
        plot.setSectionPaint("正向引导", ReportAnswerColorEnum.POSITIVE_GUIDANCE.getColor());
        plot.setSectionPaint("拒绝回答", ReportAnswerColorEnum.REFUSE_ANSWER.getColor());
        plot.setSectionPaint("生成违规内容", ReportAnswerColorEnum.VIOLATION_CONTENT.getColor());
        plot.setSectionPaint("无法判断", ReportAnswerColorEnum.UNKNOWN.getColor());
        // 自定义标签管理
        plot.setLabelGenerator(new CustomLabelGenerator());
        // 创建图表
        JFreeChart chart = new JFreeChart(
                null,
                JFreeChart.DEFAULT_TITLE_FONT,
                plot,
                true               // 显示图例
        );
        // 设置抗锯齿
        chart.setAntiAlias(true);
        chart.setTextAntiAlias(true);
        // 设置图例文字大小和居中
        chart.getLegend().setItemFont(new java.awt.Font("黑体", java.awt.Font.BOLD, 25));
        chart.getLegend().setHorizontalAlignment(HorizontalAlignment.CENTER);
        chart.setBackgroundPaint(Color.WHITE);

        try {
            Image chartImage = Image.getInstance(createChartImageBytes(chart));
            chartImage.scaleToFit(400, 200);
            BaseFont baseFont = BaseFont.createFont("STSong-Light", "UniGB-UCS2-H", BaseFont.NOT_EMBEDDED);
            Font titleFont = new Font(baseFont, 10, Font.NORMAL, BaseColor.GRAY); // 灰色粗体字体
            Paragraph quoteTitle = new Paragraph(String.format("负向样本已审核数量：%s/负向样本数量：%s", statistics.getManual().getTotalNegative() - statistics.getManual().getUnjudgedNegative(), statistics.getManual().getTotalNegative()), titleFont);
            quoteTitle.setIndentationLeft(90);
            document.add(quoteTitle);
            document.add(chartImage);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    /**
     * 转换为高质量图片字节
     * @param chart chart
     * @return byte[]
     * @throws Exception
     */
    private static byte[] createChartImageBytes(JFreeChart chart) throws Exception {
        ByteArrayOutputStream chartOutputStream = new ByteArrayOutputStream();
        org.jfree.chart.ChartUtils.writeChartAsPNG(chartOutputStream, chart, 1000, 600); // 高质量输出
        return chartOutputStream.toByteArray();
    }

    /**
     * 添加基本评测信息表格
     */
    private static PdfPTable createDetailsPdfTable() throws DocumentException, IOException {
        // 创建一个 4 列的表格
        PdfPTable table = new PdfPTable(4);
        BaseFont baseFont = BaseFont.createFont("STSong-Light", "UniGB-UCS2-H", BaseFont.NOT_EMBEDDED);
        Font chineseFont = new Font(baseFont, 12, Font.NORMAL, BaseColor.BLACK); // 黑色字体

        // 设置表格宽度（可选）
        table.setWidthPercentage(100); // 占页面宽度的 100%
        // 添加第一行
        for (int i = 0; i < 4; i++) {
            PdfPCell pdfPCell = new PdfPCell();
            // 字体左右居中
            pdfPCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            // 字体上下居中
            pdfPCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            pdfPCell.setFixedHeight(CELL_HEIGHT);
            pdfPCell.setBorderColor(DEFAULT_THEME_COLOR);
            if (i == 0) {
                pdfPCell.setPhrase(new com.itextpdf.text.Phrase("评测类型", chineseFont));
            } else if (i == 1) {
                pdfPCell.setPhrase(new com.itextpdf.text.Phrase("评测场景", chineseFont));
            } else if (i == 2) {
                pdfPCell.setPhrase(new com.itextpdf.text.Phrase("评测类型", chineseFont));
            } else {
                pdfPCell.setPhrase(new Phrase("数量", chineseFont));
            }
            table.addCell(pdfPCell);
        }

        // 添加第二行
        for (int i = 0; i < 4; i++) {
            PdfPCell pdfPCell = new PdfPCell();
            // 字体左右居中
            pdfPCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            // 字体上下居中
            pdfPCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            pdfPCell.setFixedHeight(CELL_HEIGHT);
            pdfPCell.setBorderColor(DEFAULT_THEME_COLOR);
            // 设置背景颜色
            pdfPCell.setBackgroundColor(BACKGROUND_COLOR);
            if (i == 0) {
                pdfPCell.setBackgroundColor(BaseColor.WHITE);
                pdfPCell.setPhrase(new com.itextpdf.text.Phrase("生成内容安全评估", chineseFont));
            } else if (i == 1) {
                pdfPCell.setPhrase(new com.itextpdf.text.Phrase("违反社会主义核心价值观", chineseFont));
            } else if (i == 2) {
                pdfPCell.setPhrase(new com.itextpdf.text.Phrase("文本", chineseFont));
            } else {
                pdfPCell.setPhrase(new Phrase("5", chineseFont));
            }
            table.addCell(pdfPCell);
        }

        // 添加第三行
        for (int i = 0; i < 4; i++) {
            PdfPCell pdfPCell = new PdfPCell();
            // 字体左右居中
            pdfPCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            // 字体上下居中
            pdfPCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            pdfPCell.setFixedHeight(CELL_HEIGHT);
            pdfPCell.setBorderColor(DEFAULT_THEME_COLOR);
            // 设置背景颜色
            pdfPCell.setBackgroundColor(BACKGROUND_COLOR);
            if (i == 0) {
                pdfPCell.setPhrase(new com.itextpdf.text.Phrase("总样本量", chineseFont));
            } else if (i == 1) {
                pdfPCell.setPhrase(new com.itextpdf.text.Phrase(" ", chineseFont));
            } else if (i == 2) {
                pdfPCell.setPhrase(new com.itextpdf.text.Phrase(" ", chineseFont));
            } else {
                pdfPCell.setPhrase(new Phrase("5", chineseFont));
            }
            table.addCell(pdfPCell);
        }
        return table;
    }


    /**
     * 添加基本评测信息详情表格
     */
    private static PdfPTable createDetailsItemPdfTable() throws DocumentException, IOException {

        List<String> titleList = new ArrayList<String>() {{
            add("测评类型");
            add("测评场景");
            add("样本数量");
            add("风险分类");
            add("测评指标及要求");
            add("指标测试结果");
            add("评测结果");
        }};

        // 创建一个 7 列的表格
        PdfPTable table = new PdfPTable(7);
        BaseFont baseFont = BaseFont.createFont("STSong-Light", "UniGB-UCS2-H", BaseFont.NOT_EMBEDDED);
        Font chineseFont = new Font(baseFont, 12, Font.NORMAL, BaseColor.BLACK); // 黑色字体
        Font customFont = new Font(baseFont, 7, Font.NORMAL, new BaseColor(92, 157, 186)); // 黑色字体
        // 设置表格宽度（可选）
        table.setWidthPercentage(100); // 占页面宽度的 100%

        // 添加第一行
        for (int i = 0; i < 7; i++) {
            PdfPCell pdfPCell = new PdfPCell();
            // 字体左右居中
            pdfPCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            // 字体上下居中
            pdfPCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            pdfPCell.setFixedHeight(CELL_HEIGHT);
            pdfPCell.setBorderColor(DEFAULT_THEME_COLOR);
            pdfPCell.setPhrase(new com.itextpdf.text.Phrase(titleList.get(i), chineseFont));
            table.addCell(pdfPCell);
        }

        // 总行数
        int sumCount = 10;
        // 创建第一列跨 10 行的单元格
        PdfPCell firstColumnCell = new PdfPCell();
        firstColumnCell.setRowspan(sumCount + 2); // 跨 10 行
        firstColumnCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        firstColumnCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        firstColumnCell.setPhrase(new com.itextpdf.text.Phrase("综合测评", chineseFont)); // 第一列内容
        firstColumnCell.setBorderColor(DEFAULT_THEME_COLOR);
        table.addCell(firstColumnCell);


        // 创建“测评场景”列跨 9 行的单元格
        PdfPCell sceneCell = new PdfPCell();
        sceneCell.setRowspan(9); // 跨 9 行
        sceneCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        sceneCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        sceneCell.setFixedHeight(CELL_HEIGHT * 9); // 高度为 9 行的高度
        sceneCell.setBorderColor(DEFAULT_THEME_COLOR);
        sceneCell.setPhrase(new com.itextpdf.text.Phrase("违反社会主义核心价值观", chineseFont)); // 测评场景内容
        table.addCell(sceneCell);

        // 模拟生成 9 条数据（从第 2 行开始填充）
        for (int row = 0; row < 9; row++) {
            List<String> rowData = Arrays.asList(
                    "样本" + (row + 1),
                    "风险" + (row + 1),
                    "指标" + (row + 1),
                    "测试结果" + (row + 1)
            );

            // 遍历每行的数据并添加到表格中
            for (int col = 0; col < rowData.size(); col++) {
                PdfPCell dataCell = new PdfPCell();
                dataCell.setHorizontalAlignment(Element.ALIGN_CENTER);
                dataCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
                dataCell.setFixedHeight(CELL_HEIGHT);
                dataCell.setBorderColor(DEFAULT_THEME_COLOR);
                dataCell.setPhrase(new com.itextpdf.text.Phrase(rowData.get(col), chineseFont));
                table.addCell(dataCell);
            }

            PdfPCell checkBoxCell = new PdfPCell();
            checkBoxCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            checkBoxCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            checkBoxCell.setFixedHeight(CELL_HEIGHT);
            checkBoxCell.setBorderColor(DEFAULT_THEME_COLOR);
            checkBoxCell.setPhrase(new com.itextpdf.text.Phrase("√ 符合  X 不合符", customFont));
            table.addCell(checkBoxCell);
        }


        // 剩余行数据
        // 创建“测评场景”列跨 9 行的单元格
        PdfPCell sceneCell2 = new PdfPCell();
        sceneCell2.setRowspan(2); // 跨 2 行
        sceneCell2.setHorizontalAlignment(Element.ALIGN_CENTER);
        sceneCell2.setVerticalAlignment(Element.ALIGN_MIDDLE);
        sceneCell2.setFixedHeight(CELL_HEIGHT * 2); // 高度为 9 行的高度
        sceneCell2.setBorderColor(DEFAULT_THEME_COLOR);
        sceneCell2.setPhrase(new com.itextpdf.text.Phrase("全场景评估", chineseFont)); // 测评场景内容
        table.addCell(sceneCell2);


        // 模拟生成 2 条数据（从第 2 行开始填充）
        for (int row = 0; row < 2; row++) {
            List<String> rowData = Arrays.asList(
                    "样本数量" + (row + 1),
                    "-",
                    "指标" + (row + 1),
                    "测试结果" + (row + 1)
            );

            // 遍历每行的数据并添加到表格中
            for (int col = 0; col < rowData.size(); col++) {
                PdfPCell dataCell = new PdfPCell();
                dataCell.setHorizontalAlignment(Element.ALIGN_CENTER);
                dataCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
                dataCell.setFixedHeight(CELL_HEIGHT);
                dataCell.setBorderColor(DEFAULT_THEME_COLOR);
                dataCell.setPhrase(new com.itextpdf.text.Phrase(rowData.get(col), chineseFont));
                table.addCell(dataCell);
            }

            PdfPCell checkBoxCell = new PdfPCell();
            checkBoxCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            checkBoxCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            checkBoxCell.setFixedHeight(CELL_HEIGHT);
            checkBoxCell.setBorderColor(DEFAULT_THEME_COLOR);
            checkBoxCell.setPhrase(new com.itextpdf.text.Phrase("√ 符合  X 不合符", customFont));
            table.addCell(checkBoxCell);
        }
        return table;
    }

    /**
     * 创建正向题库详情内容
     */
    private static void createForwardReviewDetailsDisplay(Document document,
                                                          Map<String, List<TaskAnswerAbnormalResp>> taskAnswerByAbnormalViolation) throws DocumentException, IOException {

        if (taskAnswerByAbnormalViolation.containsKey(QuestionCategoryEnum.FORWARD.name())) {
            createVerticalLineChunk(document, 2, "正向题库");
            PdfPTable spanTable = new PdfPTable(1);
            spanTable.setWidthPercentage(100);

            taskAnswerByAbnormalViolation.get(QuestionCategoryEnum.FORWARD.name()).forEach(violation -> {
                PdfPCell spanCell = new PdfPCell();
                spanCell.setBorderColor(new BaseColor(101, 159, 101));
                // 创建一个 2 列的表格
                PdfPTable table = new PdfPTable(new float[]{10, 150});
                table.setWidthPercentage(100);
                try {
                    // 标签
                    title(table, violation);
                    // 内容
                    content(table, violation);
                    // 答案
                    answer(table, violation);
                } catch (RuntimeException | DocumentException | IOException e) {
                    log.error("创建正向题库详情内容异常", e);
                }
                spanCell.addElement(table);
                // 添加一个空白单元格作为间隔
                PdfPCell blankCell = new PdfPCell();
                blankCell.setFixedHeight(20f); // 设置固定高度为20点
                blankCell.setBorder(PdfPCell.NO_BORDER); // 不显示边框
                spanTable.addCell(blankCell);
                // 添加外层表格
                spanTable.addCell(spanCell);
            });
            document.add(spanTable);
        }
    }

    /**
     * 创建负向题库详情内容
     */
    private static void createNegativeReviewDetailsDisplay(Document document, Map<String, List<TaskAnswerAbnormalResp>> taskAnswerByAbnormalViolation) throws DocumentException, IOException {
        if (taskAnswerByAbnormalViolation.containsKey(QuestionCategoryEnum.NEGATIVE.name())) {
            createVerticalLineChunk(document, 2, "负向题库");

            PdfPTable spanTable = new PdfPTable(1);
            spanTable.setWidthPercentage(100);
            taskAnswerByAbnormalViolation.get(QuestionCategoryEnum.NEGATIVE.name()).forEach(violation -> {
                PdfPCell spanCell = new PdfPCell();
                spanCell.setBorderColor(new BaseColor(101, 159, 101));
                // 创建一个 2 列的表格
                PdfPTable table = new PdfPTable(new float[]{10, 150});
                table.setWidthPercentage(100);
                try {
                    // 标签
                    title(table, violation);
                    // 内容
                    content(table, violation);
                    // 答案
                    answer(table, violation);
                } catch (RuntimeException | DocumentException | IOException e) {
                    log.error("创建正向题库详情内容异常", e);
                }
                spanCell.addElement(table);
                // 添加一个空白单元格作为间隔
                PdfPCell blankCell = new PdfPCell();
                blankCell.setFixedHeight(20f); // 设置固定高度为20点
                blankCell.setBorder(PdfPCell.NO_BORDER); // 不显示边框
                spanTable.addCell(blankCell);
                // 添加外层表格
                spanTable.addCell(spanCell);
            });
            document.add(spanTable);
        }
    }


    private static void title(PdfPTable table, TaskAnswerAbnormalResp resp) throws DocumentException, IOException {
        BaseFont baseFont = BaseFont.createFont("STSong-Light", "UniGB-UCS2-H", BaseFont.NOT_EMBEDDED);
        com.itextpdf.text.Font chineseFont = new com.itextpdf.text.Font(baseFont, 12, com.itextpdf.text.Font.NORMAL, new BaseColor(101, 159, 101)); // 主题颜色字体

        // 添加第一行
        PdfPCell pdfPCell = new PdfPCell();
        // 字体左右居中
        pdfPCell.setHorizontalAlignment(Element.ALIGN_LEFT);
        // 字体上下居中
        pdfPCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        pdfPCell.setBorder(PdfPCell.NO_BORDER);
        pdfPCell.setColspan(2);

        // 使用 Paragraph 添加多行文本
        Paragraph paragraph = new Paragraph();
        paragraph.setIndentationLeft(30);
        String tags = "";
        if (StringUtils.isBlank(resp.getFirstTag())) {
            tags = "无";
        } else {
            tags = resp.getFirstTag();
            if (StringUtils.isNotBlank(resp.getSecondTag())) {
                tags += "、" + resp.getSecondTag();
            }
        }
        paragraph.add(new Chunk(String.format("标签：%s\n", tags), chineseFont));

        // 创建一条水平分割线
        LineSeparator lineSeparator = new LineSeparator();
        lineSeparator.setOffset(-5f);  // 可选：调整线条的垂直偏移量
        lineSeparator.setLineColor(new BaseColor(242, 242, 242)); // 设置颜色为蓝色
        lineSeparator.setLineWidth(0.5f); // 设置线宽，默认是1

        // 将分割线作为一个段落添加进去
        paragraph.add(new Chunk(lineSeparator));

        pdfPCell.addElement(paragraph); // 注意这里使用 addElement() 方法

        table.addCell(pdfPCell);
    }

    private static void content(PdfPTable table, TaskAnswerAbnormalResp resp) throws DocumentException, IOException {
        BaseFont baseFont = BaseFont.createFont("STSong-Light", "UniGB-UCS2-H", BaseFont.NOT_EMBEDDED);
        com.itextpdf.text.Font chineseFont = new com.itextpdf.text.Font(baseFont, 11, com.itextpdf.text.Font.NORMAL, BaseColor.BLACK); // 黑色字体
        com.itextpdf.text.Font iconFont = new com.itextpdf.text.Font(baseFont, 12, com.itextpdf.text.Font.NORMAL, BaseColor.WHITE); // 白色字体


        // 添加第二行
        PdfPCell iconPCell = new PdfPCell();
        // 字体左右居右
        iconPCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        iconPCell.setBorder(PdfPCell.NO_BORDER);
        // 使用 Paragraph 添加多行文本
        Paragraph iconParagraph = new Paragraph();
        iconParagraph.setAlignment(Element.ALIGN_RIGHT);
        iconParagraph.add(new Chunk("问", iconFont).setBackground(new BaseColor(102, 102, 255)));
        iconPCell.addElement(iconParagraph); // 注意这里使用 addElement() 方法
        table.addCell(iconPCell);


        // 添加第二行
        PdfPCell pdfPCell = new PdfPCell();
        pdfPCell.setHorizontalAlignment(Element.ALIGN_LEFT);
        // 字体左右居右
        pdfPCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        pdfPCell.setBorder(PdfPCell.NO_BORDER);

        // 使用 Paragraph 添加多行文本
        Paragraph paragraph = new Paragraph();
        paragraph.add(new Chunk(resp.getQuestionContent() + "\n", chineseFont));

        // 创建一条水平分割线
        LineSeparator lineSeparator = new LineSeparator();
        lineSeparator.setOffset(-5f);  // 可选：调整线条的垂直偏移量
        lineSeparator.setLineColor(new BaseColor(242, 242, 242));
        lineSeparator.setLineWidth(0.5f); // 设置线宽，默认是1

        // 将分割线作为一个段落添加进去
        paragraph.add(new Chunk(lineSeparator));

        pdfPCell.addElement(paragraph); // 注意这里使用 addElement() 方法
        table.addCell(pdfPCell);
    }

    private static void answer(PdfPTable table, TaskAnswerAbnormalResp resp) throws DocumentException, IOException {
        BaseFont baseFont = BaseFont.createFont("STSong-Light", "UniGB-UCS2-H", BaseFont.NOT_EMBEDDED);
        com.itextpdf.text.Font chineseFont = new com.itextpdf.text.Font(baseFont, 11, com.itextpdf.text.Font.NORMAL, BaseColor.BLACK); // 黑色字体
        com.itextpdf.text.Font iconFont = new com.itextpdf.text.Font(baseFont, 12, com.itextpdf.text.Font.NORMAL, BaseColor.WHITE); // 白色字体

        // 添加第二行
        PdfPCell iconPCell = new PdfPCell();
        // 字体左右居右
        iconPCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        iconPCell.setBorder(PdfPCell.NO_BORDER);
        // 使用 Paragraph 添加多行文本
        Paragraph iconParagraph = new Paragraph();
        iconParagraph.setAlignment(Element.ALIGN_RIGHT);
        iconParagraph.add(new Chunk("答", iconFont).setBackground(new BaseColor(102, 102, 255)));
        iconPCell.addElement(iconParagraph); // 注意这里使用 addElement() 方法
        table.addCell(iconPCell);


        // 添加第二行
        PdfPCell pdfPCell = new PdfPCell();
        // 字体居左
        pdfPCell.setHorizontalAlignment(Element.ALIGN_LEFT);
        // 字体上下居中
        pdfPCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        pdfPCell.setBorder(PdfPCell.NO_BORDER);

        // 使用 Paragraph 添加多行文本
        Paragraph paragraph = new Paragraph();
        paragraph.add(new Chunk(resp.getAnswerContent() + "\n", chineseFont));

        pdfPCell.addElement(paragraph); // 注意这里使用 addElement() 方法
        table.addCell(pdfPCell);
    }
}
