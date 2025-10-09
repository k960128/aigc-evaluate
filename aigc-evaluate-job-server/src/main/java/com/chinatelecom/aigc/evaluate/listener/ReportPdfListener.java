package com.chinatelecom.aigc.evaluate.listener;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.ObjectUtil;
import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chinatelecom.aigc.evaluate.common.enums.QuestionCategoryEnum;
import com.chinatelecom.aigc.evaluate.common.enums.ReportAnswerColorEnum;
import com.chinatelecom.aigc.evaluate.common.exception.ServiceException;
import com.chinatelecom.aigc.evaluate.common.util.date.DateUtils;
import com.chinatelecom.aigc.evaluate.common.util.file.FileUtil;
import com.chinatelecom.aigc.evaluate.domain.ModelInfoDO;
import com.chinatelecom.aigc.evaluate.domain.QuestionSetInfoDO;
import com.chinatelecom.aigc.evaluate.domain.ReportInfoDO;
import com.chinatelecom.aigc.evaluate.dto.model.TaskReportStep;
import com.chinatelecom.aigc.evaluate.dto.req.ReportSaveReq;
import com.chinatelecom.aigc.evaluate.dto.resp.*;
import com.chinatelecom.aigc.evaluate.jfree.CustomLabelGenerator;
import com.chinatelecom.aigc.evaluate.jfree.CustomRingPlot;
import com.chinatelecom.aigc.evaluate.mapper.ReportInfoMapper;
import com.chinatelecom.aigc.evaluate.mq.execute.ExecutorQueueService;
import com.chinatelecom.aigc.evaluate.mq.param.MessageBody;
import com.chinatelecom.aigc.evaluate.service.QuestionSetItemService;
import com.chinatelecom.aigc.evaluate.service.QuestionSetService;
import com.chinatelecom.aigc.evaluate.service.TaskAnswerService;
import com.chinatelecom.aigc.evaluate.web.websocket.handler.WebSocketHandler;
import com.chinatelecom.aigc.evaluate.web.websocket.message.Message;
import com.itextpdf.text.*;
import com.itextpdf.text.Font;
import com.itextpdf.text.Image;
import com.itextpdf.text.Rectangle;
import com.itextpdf.text.pdf.*;
import com.itextpdf.text.pdf.draw.LineSeparator;
import com.itextpdf.text.pdf.draw.VerticalPositionMark;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.labels.StandardCategoryToolTipGenerator;
import org.jfree.chart.plot.SpiderWebPlot;
import org.jfree.chart.title.TextTitle;
import org.jfree.chart.ui.HorizontalAlignment;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.data.general.DefaultPieDataset;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.stream.Collectors;


import static com.chinatelecom.aigc.evaluate.common.exception.enums.ErrorCodeConstants.REPORT_GENERATE_ERROR;
import static com.chinatelecom.aigc.evaluate.common.exception.util.ServiceExceptionUtil.exception;

/**
 * PDF生成报告监听器
 */
@Slf4j
@Component
public class ReportPdfListener {

    // 消息队列主题
    private static final String MSG_KEY = "REPORT_PDF";
    // 后缀
    private static final String SUFFIX = ".pdf";
    // 默认文件路径
    private static final String REPORT_FILE_PATH = "%s%s-%s" + SUFFIX;
    private static final int CAPACITY = 2000;
    private static final int PAGE_SIZE = 500;
    // 默认单元格行高
    private static final Integer DEFAULT_CELL_HEIGHT = 30;
    // 默认背景颜色
    private static final BaseColor BACKGROUND_COLOR = new BaseColor(237, 252, 237);
    // 默认主题颜色
    private static final BaseColor DEFAULT_THEME_COLOR = new BaseColor(101, 159, 101);

    private final ForkJoinPool forkJoinPool = new ForkJoinPool(10);
    ExecutorService executor = Executors.newFixedThreadPool(3);
    // 正向题库队列
    private final BlockingQueue<TaskAnswerAbnormalResp> frowordBlockingQueue = new LinkedBlockingQueue<>(CAPACITY);
    // 负向题库队列
    private final BlockingQueue<TaskAnswerAbnormalResp> negativeBlockingQueue = new LinkedBlockingQueue<>(CAPACITY);
    private final TaskAnswerAbnormalResp POSITION = new TaskAnswerAbnormalResp();

    private final TaskAnswerService taskAnswerService;
    private final QuestionSetService questionSetService;
    private final QuestionSetItemService questionSetItemService;
    private final ExecutorQueueService executorQueueService;
    private final ReportInfoMapper reportInfoMapper;
    private final WebSocketHandler webSocketHandler;
    public ReportPdfListener(TaskAnswerService taskAnswerService,
                             QuestionSetService questionSetService,
                             QuestionSetItemService questionSetItemService,
                             ExecutorQueueService executorQueueService,
                             ReportInfoMapper reportInfoMapper,
                             WebSocketHandler webSocketHandler) {
        this.taskAnswerService = taskAnswerService;
        this.questionSetService = questionSetService;
        this.questionSetItemService = questionSetItemService;
        this.executorQueueService = executorQueueService;
        this.reportInfoMapper = reportInfoMapper;
        this.webSocketHandler = webSocketHandler;
    }

    @PostConstruct
    public void init() {
        Thread consumerThread = new Thread(() -> {
            while (true) {
                try {
                    MessageBody messageBody = executorQueueService.consumer(MSG_KEY);
                    if (messageBody == null) {
                        Thread.sleep(100);
                        continue;
                    }
                    handleMessage(messageBody);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("PDF消费者线程被中断");
                    break;
                } catch (Exception e) {
                    log.error("PDF消费异常", e);
                }
            }
        }, "Pdf-Consumer-Thread");

        consumerThread.start();
    }

    private void handleMessage(MessageBody messageBody) {
        try {
            ReportResp reportResp = generateReportPDF(messageBody);

            // 发送给 WebSocket 客户端
            Message msg = new Message("pdf_report", reportResp);
            webSocketHandler.sendToClient(messageBody.getUserId(), msg);

            log.info("PDF生成成功：msgID:{} bean:{}", messageBody.getMsgId(), JSON.toJSONString(reportResp));
        } catch (ServiceException e) {
            throw exception(REPORT_GENERATE_ERROR, e.getMessage());
        }
    }

    /**
     * 开始执行生成PDF
     * @param messageBody msgBody
     * @return resp
     */
    private ReportResp generateReportPDF(MessageBody messageBody) {
        String msgId = messageBody.getMsgId();
        TaskReportStep taskReportStep = JSON.parseObject(messageBody.getBody(), TaskReportStep.class);
        // 获取任务基本信息
        JobResp jobInfo = taskReportStep.getJobInfo();
        // 获取模型基本信息
        ModelInfoDO modelInfoDO = jobInfo.getModelInfoMap().get(Long.valueOf(taskReportStep.getModelId()));
        String reportPath = null;
        // 任务异步编排
        // 使用CompletableFuture并行执行三个业务调用
        // 获取题集信息
        CompletableFuture<EvaluateResultStatisticsGroupResp> statisticsFuture = CompletableFuture.supplyAsync(() ->
                taskAnswerService.getEvaluateResultStatisticsByTaskIdAndModelId(
                        Long.parseLong(taskReportStep.getTaskId()),
                        Long.parseLong(taskReportStep.getModelId())
                ), executor
        );
        // 获取题集题目数量分布数据
        CompletableFuture<QuestionSetInfoDO> questionSetInfoFuture = CompletableFuture.supplyAsync(() ->
                questionSetService.get(jobInfo.getQuestionSet().get(0).getId(), true), executor
        );
        // 收集异常题目详情
        CompletableFuture<List<QuestionSetItemStatisticsResp>> questionSetStatisticsFuture = CompletableFuture.supplyAsync(() ->
                questionSetItemService.getQuestionSetStatistic(jobInfo.getQuestionSet().get(0).getId(), true), executor
        );

        // 合并所有结果
        CompletableFuture<Void> allFutures = CompletableFuture.allOf(
                statisticsFuture, questionSetInfoFuture, questionSetStatisticsFuture
        );

        // 开始处理业务逻辑
        Document document = null;

        // 等待所有任务完成并获取结果
        try {
            // 等待所有任务完成
            allFutures.join();

            // 获取各个任务的结果
            EvaluateResultStatisticsGroupResp statistics = statisticsFuture.get();
            QuestionSetInfoDO questionSetInfo = questionSetInfoFuture.get();
            List<QuestionSetItemStatisticsResp> questionSetStatistics = questionSetStatisticsFuture.get();

            String safeName = taskReportStep.getName().replaceAll("[\\\\/:*?\"<>|]", "_");
            String fileName = String.format("%s-%s.pdf", safeName, msgId);
            Path dirPath = Paths.get(FileUtil.getReportPath());
            if (!Files.exists(dirPath)) {
                Files.createDirectories(dirPath);
            }
            reportPath = dirPath.resolve(fileName).toString();

            document = new Document();
            PdfWriter pdfWriter = PdfWriter.getInstance(document, Files.newOutputStream(Paths.get(reportPath)));
            // 设置PDF页码监听器
            pdfWriter.setPageEvent(new PageNumberingEventListener());
            document.open();
            // 生成 Document 并添加内容
            generateDocument(document, jobInfo, modelInfoDO, statistics, questionSetInfo, questionSetStatistics);
        } catch (InterruptedException | ExecutionException e) {
            // 处理异常
            throw new RuntimeException("执行并行任务时出错", e);
        } catch (IOException e) {
            throw new RuntimeException("文件夹创建失败：{}", e);
        } catch (DocumentException e) {
            throw new RuntimeException(e);
        } finally {
            if (document != null && document.isOpen()) {
                document.close();
            }
        }

        String jobName = jobInfo != null ? jobInfo.getName() : String.valueOf(taskReportStep.getTaskId());
        String modelName = modelInfoDO != null ? modelInfoDO.getModelName() : String.valueOf(taskReportStep.getModelId());
        String message = String.format("任务[%s]、模型[%s] 的报告[%s] 已生成完成，请点击查看。",
                jobName,
                modelName,
                taskReportStep.getName()
        );

        ReportInfoDO reportInfoDO = reportInfoMapper.selectReport(taskReportStep.getUserId(), taskReportStep.getTaskId(), taskReportStep.getModelId(), taskReportStep.getReportType());
        if (ObjectUtil.isNotNull(reportInfoDO)) {
            reportInfoMapper.deleteById(reportInfoDO);
        }

        reportInfoDO = ReportInfoDO.create(ReportSaveReq.builder()
                .userId(taskReportStep.getUserId())
                .taskId(taskReportStep.getTaskId())
                .modelId(taskReportStep.getModelId())
                .name(taskReportStep.getName())
                .reportType(taskReportStep.getReportType())
                .desc(message)
                .build());
        reportInfoDO.setFilePath(reportPath);

        reportInfoMapper.insert(reportInfoDO);

        return ReportResp.builder()
                .userId(taskReportStep.getUserId())
                .taskId(taskReportStep.getTaskId())
                .modelId(taskReportStep.getModelId())
                .name(taskReportStep.getName())
                .desc(message)
                .build();
    }

    private BufferedImage generateRadarChart(EvaluateResultStatisticsGroupResp statistics, String title) {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        String series = "合格率";

        dataset.addValue(100, series, "无");
        dataset.addValue(47.83, series, "翻译");
        dataset.addValue(77.78, series, "跨类劝持");
        dataset.addValue(100, series, "同类劝持");
        dataset.addValue(100, series, "不安全的指令主题");
        dataset.addValue(100, series, "目标劝持");
        dataset.addValue(98.18, series, "限制生成拒绝词");
        dataset.addValue(97.67, series, "前缀攻击");
        dataset.addValue(94.95, series, "角色扮演");
        dataset.addValue(56.96, series, "payload 拆分");
        dataset.addValue(98.61, series, "隐含不安全观点的询问");
        dataset.addValue(100, series, "反面诱导");

        SpiderWebPlot plot = new SpiderWebPlot(dataset);
        plot.setStartAngle(54);
        plot.setInteriorGap(0.05); // 更小的间距，尽量贴近长方形边界
        plot.setMaxValue(100);
        plot.setWebFilled(true);

        plot.setSeriesPaint(0, new Color(30, 144, 255));
        plot.setBaseSeriesOutlinePaint(Color.GRAY);
        plot.setBaseSeriesOutlineStroke(new BasicStroke(1.2f));
        plot.setAxisLinePaint(new Color(180, 180, 180));
        plot.setAxisLineStroke(new BasicStroke(0.8f));
        plot.setLabelFont(new java.awt.Font("微软雅黑", java.awt.Font.PLAIN, 14)); // 更大字体
        plot.setLabelPaint(Color.DARK_GRAY);
        plot.setOutlineVisible(false);

        JFreeChart chart = new JFreeChart("", new java.awt.Font("微软雅黑", java.awt.Font.PLAIN, 14), plot, false);
        chart.setTitle(new TextTitle(title, new java.awt.Font("微软雅黑", java.awt.Font.BOLD, 16)));
        chart.setBackgroundPaint(Color.white);

        chart.setAntiAlias(true);
        chart.setTextAntiAlias(true);

        return chart.createBufferedImage(800, 600); // 提升分辨率
    }




    /**
     * 生成PDF节点
     */
    private void generateDocument(Document document,
                                  JobResp jobInfo,
                                  ModelInfoDO modelInfoDO,
                                  EvaluateResultStatisticsGroupResp statistics,
                                  QuestionSetInfoDO questionSetInfo,
                                  List<QuestionSetItemStatisticsResp> questionSetStatistics) throws DocumentException, IOException {
        // 1.基本评测信息组装
        generateBasicInfo(document, jobInfo, questionSetInfo, questionSetStatistics);
        // 2.评测指标总览组装
        overviewEvaluationIndicators(document, jobInfo, modelInfoDO, statistics);
        // 3.评测指标详情
        detailsEvaluationIndicators(document, jobInfo, modelInfoDO, statistics);
        // 4.雷达图
        addRadarChartSection(document, statistics, "题集答题分布雷达图");
        // 5.评测详情
        reviewDetailsDisplay(document, jobInfo.getId(), modelInfoDO.getId());

    }


    private void addRadarChartSection(Document document,
                                      EvaluateResultStatisticsGroupResp statistics,
                                      String title) throws DocumentException, IOException {
        // 1. 生成雷达图 BufferedImage
        BufferedImage radarImage = generateRadarChart(statistics, title);

        // 2. 转为 iText Image
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(radarImage, "png", baos);
        Image image = Image.getInstance(baos.toByteArray());

        // 3. 设置图片属性
        image.setAlignment(Element.ALIGN_CENTER);
        image.scaleToFit(400f, 300f);

        // 4. 添加标题段落
        Paragraph chartTitle = new Paragraph("题集难度分布雷达图",
                FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, BaseColor.BLACK));
        chartTitle.setAlignment(Element.ALIGN_CENTER);
        chartTitle.setSpacingAfter(10f);
        document.add(chartTitle);

        // 5. 封装为单元格
        PdfPCell imageCell = new PdfPCell(image, true);
        imageCell.setBorder(Rectangle.BOX);
        imageCell.setBorderColor(BaseColor.LIGHT_GRAY);
        imageCell.setPadding(5f);
        imageCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        imageCell.setVerticalAlignment(Element.ALIGN_MIDDLE);

        // 6. 表格包裹图像
        PdfPTable table = new PdfPTable(1);
        table.setWidthPercentage(50);
        table.setHorizontalAlignment(Element.ALIGN_CENTER);
        table.addCell(imageCell);

        // 7. 添加到文档
        document.add(table);
    }


    /**
     * 1.基本评测信息组装
     */
    private void generateBasicInfo(Document document,
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
    private PdfPTable createBasicPdfTable(JobResp jobInfo,
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
            pdfPCell.setFixedHeight(DEFAULT_CELL_HEIGHT);
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
            pdfPCell.setFixedHeight(DEFAULT_CELL_HEIGHT);
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

        /*
        // 添加第三行
        PdfPCell header3 = new PdfPCell();
        header3.setFixedHeight(DEFAULT_CELL_HEIGHT);
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
        pdfPCellForward.setFixedHeight(DEFAULT_CELL_HEIGHT);
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
        pdfPCellNegative.setFixedHeight(DEFAULT_CELL_HEIGHT);
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
        */

        // 添加第五行
        for (int i = 0; i < 4; i++) {
            PdfPCell pdfPCell = new PdfPCell();
            pdfPCell.setFixedHeight(DEFAULT_CELL_HEIGHT);
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
    private void overviewEvaluationIndicators(Document document,
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
    private void detailsEvaluationIndicators(Document document,
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
    private void reviewDetailsDisplay(Document document,
                                      Long jobId,
                                      Long modelId) throws DocumentException, IOException {
        // 添加引用标题
        createVerticalLineChunk(document, 1, "评测详情");
        // 构建正向题库异常题目信息
        createForwardReviewDetailsDisplay(document, jobId, modelId);
        // 构建负向题库异常题目信息
        createNegativeReviewDetailsDisplay(document, jobId, modelId);
    }

    /**
     * 创建引用标题
     */
    private void createVerticalLineChunk(Document document,
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
    private float getLineWidthByLevel(int level) {
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
    private void createManualForwardEvaluationPdfRingPie(Document document,
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
    private void createManualNegativeEvaluationPdfRingPie(Document document,
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
    private byte[] createChartImageBytes(JFreeChart chart) throws Exception {
        ByteArrayOutputStream chartOutputStream = new ByteArrayOutputStream();
        org.jfree.chart.ChartUtils.writeChartAsPNG(chartOutputStream, chart, 600, 400); // 高质量输出
        return chartOutputStream.toByteArray();
    }

    /**
     * 添加基本评测信息表格
     */
    private PdfPTable createDetailsPdfTable() throws DocumentException, IOException {
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
            pdfPCell.setFixedHeight(DEFAULT_CELL_HEIGHT);
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
            pdfPCell.setFixedHeight(DEFAULT_CELL_HEIGHT);
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
            pdfPCell.setFixedHeight(DEFAULT_CELL_HEIGHT);
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
    private PdfPTable createDetailsItemPdfTable() throws DocumentException, IOException {

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
            pdfPCell.setFixedHeight(DEFAULT_CELL_HEIGHT);
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
        sceneCell.setFixedHeight(DEFAULT_CELL_HEIGHT * 9); // 高度为 9 行的高度
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
                dataCell.setFixedHeight(DEFAULT_CELL_HEIGHT);
                dataCell.setBorderColor(DEFAULT_THEME_COLOR);
                dataCell.setPhrase(new com.itextpdf.text.Phrase(rowData.get(col), chineseFont));
                table.addCell(dataCell);
            }

            PdfPCell checkBoxCell = new PdfPCell();
            checkBoxCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            checkBoxCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            checkBoxCell.setFixedHeight(DEFAULT_CELL_HEIGHT);
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
        sceneCell2.setFixedHeight(DEFAULT_CELL_HEIGHT * 2); // 高度为 9 行的高度
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
                dataCell.setFixedHeight(DEFAULT_CELL_HEIGHT);
                dataCell.setBorderColor(DEFAULT_THEME_COLOR);
                dataCell.setPhrase(new com.itextpdf.text.Phrase(rowData.get(col), chineseFont));
                table.addCell(dataCell);
            }

            PdfPCell checkBoxCell = new PdfPCell();
            checkBoxCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            checkBoxCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            checkBoxCell.setFixedHeight(DEFAULT_CELL_HEIGHT);
            checkBoxCell.setBorderColor(DEFAULT_THEME_COLOR);
            checkBoxCell.setPhrase(new com.itextpdf.text.Phrase("√ 符合  X 不合符", customFont));
            table.addCell(checkBoxCell);
        }
        return table;
    }

    /**
     * 创建正向题库详情内容
     */
    private void createForwardReviewDetailsDisplay(Document document,
                                                   Long jobId,
                                                   Long modelId) throws DocumentException, IOException {
        try {
            int currentPage = 1;
            Page<TaskAnswerAbnormalResp> page = taskAnswerService.pageQueryTaskAnswerByAbnormalViolation(currentPage, PAGE_SIZE, jobId, modelId, QuestionCategoryEnum.FORWARD.name());
            if (CollectionUtil.isNotEmpty(page.getRecords())) {
                createVerticalLineChunk(document, 2, "正向题库");
                PdfPTable spanTable = new PdfPTable(1);
                spanTable.setWidthPercentage(100);

                // 添加读数操作
                frowordBlockingQueue.addAll(page.getRecords());
                //forkJoinPool.execute(() -> generateReviewDetailsDisplay(spanTable, QuestionCategoryEnum.FORWARD.name()));
                // 启动异步任务并使用 Future 等待执行完成
                Future<?> future = forkJoinPool.submit(() ->
                        generateReviewDetailsDisplay(spanTable, QuestionCategoryEnum.FORWARD.name())
                );

                while (page.hasNext()) {
                    currentPage++;
                    page = taskAnswerService.pageQueryTaskAnswerByAbnormalViolation(currentPage, PAGE_SIZE, jobId, modelId, QuestionCategoryEnum.FORWARD.name());
                    frowordBlockingQueue.addAll(page.getRecords());
                }

                // 加入毒丸对象，表示队列结束
                frowordBlockingQueue.add(POSITION);

                // 等待异步处理完成
                future.get();
                document.add(spanTable);
            }
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("生成正向题库详情失败", e);
        }
    }

    /**
     * 创建负向题库详情内容
     */
    private void createNegativeReviewDetailsDisplay(Document document,
                                                    Long jobId,
                                                    Long modelId) throws DocumentException, IOException {
        try {
            int currentPage = 1;
            Page<TaskAnswerAbnormalResp> page = taskAnswerService.pageQueryTaskAnswerByAbnormalViolation(currentPage, PAGE_SIZE, jobId, modelId, QuestionCategoryEnum.NEGATIVE.name());
            if (CollectionUtil.isNotEmpty(page.getRecords())) {
                createVerticalLineChunk(document, 2, "负向题库");
                PdfPTable spanTable = new PdfPTable(1);
                spanTable.setWidthPercentage(100);

                // 添加读数操作
                negativeBlockingQueue.addAll(page.getRecords());
                //forkJoinPool.execute(() -> generateReviewDetailsDisplay(spanTable, QuestionCategoryEnum.NEGATIVE.name()));
                // 启动异步任务并使用 Future 等待执行完成
                Future<?> future = forkJoinPool.submit(() ->
                        generateReviewDetailsDisplay(spanTable, QuestionCategoryEnum.NEGATIVE.name())
                );

                while (page.hasNext()) {
                    currentPage++;
                    page = taskAnswerService.pageQueryTaskAnswerByAbnormalViolation(currentPage, PAGE_SIZE, jobId, modelId, QuestionCategoryEnum.NEGATIVE.name());
                    negativeBlockingQueue.addAll(page.getRecords());
                }

                // 加入毒丸对象，表示队列结束
                negativeBlockingQueue.add(POSITION);

                // 等待异步处理完成
                future.get();
                document.add(spanTable);
            }
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("生成负向题库详情失败", e);
        }
    }

    /**
     * 构建
     */
    private void generateReviewDetailsDisplay(PdfPTable spanTable, String category) {
        PdfPCell spanCell = new PdfPCell();
        spanCell.setBorderColor(new BaseColor(101, 159, 101));
        // 创建一个 2 列的表格
        PdfPTable table = new PdfPTable(new float[]{10, 150});
        table.setWidthPercentage(100);
        TaskAnswerAbnormalResp violation = null;
        if (QuestionCategoryEnum.FORWARD.name().equals(category)) {
            try {
                while (true) {
                    violation = frowordBlockingQueue.take();
                    if (violation == POSITION) {
                        break;
                    }
                    // 标签
                    title(table, violation);
                    // 内容
                    content(table, violation);
                    // 答案
                    answer(table, violation);
                }
            } catch (InterruptedException | RuntimeException | DocumentException | IOException e) {
                log.error("生成正向题库详情内容异常", e);
            }
        } else {
            try {
                while (true) {
                    violation = negativeBlockingQueue.take();
                    if (violation == POSITION) {
                        break;
                    }
                    // 标签
                    title(table, violation);
                    // 内容
                    content(table, violation);
                    // 答案
                    answer(table, violation);
                }
            } catch (InterruptedException | RuntimeException | DocumentException | IOException e) {
                log.error("生成正向题库详情内容异常", e);
            }
        }
        spanCell.addElement(table);
        // 添加一个空白单元格作为间隔
        PdfPCell blankCell = new PdfPCell();
        blankCell.setFixedHeight(20f); // 设置固定高度为20点
        blankCell.setBorder(PdfPCell.NO_BORDER); // 不显示边框
        spanTable.addCell(blankCell);
        // 添加外层表格
        spanTable.addCell(spanCell);
    }


    private void title(PdfPTable table, TaskAnswerAbnormalResp resp) throws DocumentException, IOException {
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
        if (org.apache.commons.lang3.StringUtils.isBlank(resp.getFirstTag())) {
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

    private void content(PdfPTable table, TaskAnswerAbnormalResp resp) throws DocumentException, IOException {
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

    private void answer(PdfPTable table, TaskAnswerAbnormalResp resp) throws DocumentException, IOException {
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
