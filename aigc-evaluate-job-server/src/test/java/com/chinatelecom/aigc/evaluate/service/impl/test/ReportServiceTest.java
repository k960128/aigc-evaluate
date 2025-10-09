package com.chinatelecom.aigc.evaluate.service.impl.test;

import com.chinatelecom.aigc.evaluate.JobServerApplication;
import com.chinatelecom.aigc.evaluate.common.enums.QuestionCategoryEnum;
import com.chinatelecom.aigc.evaluate.common.util.file.FileUtil;
import com.chinatelecom.aigc.evaluate.common.util.snow.CodeUtils;
import com.chinatelecom.aigc.evaluate.domain.QuestionSetInfoDO;
import com.chinatelecom.aigc.evaluate.dto.model.ExtractConf;
import com.chinatelecom.aigc.evaluate.dto.model.ExtractConfRandom;
import com.chinatelecom.aigc.evaluate.dto.model.ForwardConf;
import com.chinatelecom.aigc.evaluate.dto.model.NegativeConf;
import com.chinatelecom.aigc.evaluate.dto.req.*;
import com.chinatelecom.aigc.evaluate.mq.execute.ExecutorQueueService;
import com.chinatelecom.aigc.evaluate.mq.param.MessageBody;
import com.chinatelecom.aigc.evaluate.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.quartz.SchedulerException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ForkJoinPool;

@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest(classes = JobServerApplication.class)
public class ReportServiceTest {

    @Autowired
    private ReportService reportService;
    @Autowired
    private ExecutorQueueService executorQueueService;
    @Autowired
    private QuestionTagService questionTagService;
    @Autowired
    private QuestionService questionService;
    @Autowired
    private QuestionSetService questionSetService;
    @Autowired
    private ModelInfoService modelInfoService;
    @Autowired
    private JobService jobService;
    /**
     * todo 使用动态线程池
     */
    private final ForkJoinPool forkJoinPool = new ForkJoinPool(10);


    @Test
    public void testGetReportPath() {
        String reportPath = FileUtil.getReportPath();
        log.info("获取到的目录：{}", reportPath);
    }

    @Test
    public void generatePdf() {
        ReportSaveReq reportSaveReq = new ReportSaveReq();
        reportSaveReq.setName("报告123456");
        reportSaveReq.setTaskId("112");
        reportSaveReq.setModelId("24");
        reportService.createReport(reportSaveReq);
    }

    @Test
    public void testExecuteQueue() {
        boolean pushed = executorQueueService.product("reportPDF", new MessageBody("aaaa", String.valueOf(CodeUtils.getSnowFlakeId()), "msg消息"));
        if (pushed) {
            forkJoinPool.execute(this::customerMsg);
        }
    }


    private void customerMsg() {
        MessageBody reportPDF = executorQueueService.consumer("reportPDF");
        if (reportPDF != null) {
            log.info("获取到消息：{}", reportPDF.getBody());
        }
    }

    @Test
    public void generatePdfV2() {
        ReportSaveReq reportSaveReq = new ReportSaveReq();
        reportSaveReq.setName("报告123456");
        reportSaveReq.setUserId("112");
        reportSaveReq.setTaskId("112");
        reportSaveReq.setModelId("24");
        reportService.createReportV2(reportSaveReq);
    }

    /**
     *
     */
    @Test
    public void testSaveTag() {
        //制度、信仰、形象、文化、习俗、民族、地理、历史、英烈、性别、年龄、职业、健康、幻觉、其它
        List<String> list = Arrays.asList(
                "制度", "信仰", "形象", "文化", "习俗", "民族", "地理",
                "历史", "英烈", "性别", "年龄", "职业", "健康", "幻觉", "其它"
        );
        list.forEach(each -> {
//            questionTagService.create(QuestionTagSaveReq.builder()
//                    .tagName(each)
//                    .tagDesc(each)
//                    .category(QuestionCategoryEnum.FORWARD.getCode())
//                    .parentId("0")
//                    .build());
            log.error("执行完成：{}", each);
        });
    }

    @Test
    public void save() {
        try {
            // 替换为你的实际文件路径
            String jsonFilePath = "C:\\Users\\kant\\Desktop\\负向.json";

            List<QuestionSaveReq> questions = readQuestionsFromJson(jsonFilePath);
            log.error("读取到的数据量:{}", questions.size());
            questions.forEach(question -> {
                question.setId(null);
                question.setQuestionId(null);
                question.setCreateTime(null);
                question.setUpdateTime(null);
                question.setContentHash(null);
                questionService.create(question);
            });
        } catch (IOException e) {
            System.err.println("读取或解析 JSON 文件失败: " + e.getMessage());
            e.printStackTrace();
        }
    }


    /**
     * 从 JSON 文件读取数据，转换为 List<QuestionSaveReq>
     *
     * @param filePath JSON 文件路径
     * @return List<QuestionSaveReq>
     * @throws IOException 读取或解析失败
     */
    public List<QuestionSaveReq> readQuestionsFromJson(String filePath) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        File file = new File(filePath);
        if (!file.exists()) {
            throw new IOException("文件不存在: " + filePath);
        }

        // 先解析为 List<OuterItem>
        List<JsonDataWrapper> outerItems = objectMapper.readValue(file, new com.fasterxml.jackson.core.type.TypeReference<List<JsonDataWrapper>>() {
        });

        List<QuestionSaveReq> result = new ArrayList<>();

        // 遍历每个 OuterItem，将其 list 中的题目加入结果
        for (JsonDataWrapper item : outerItems) {
            if (item.getList() != null) {
                result.addAll(item.getList());
            }
        }

        return result;
    }


    private static class JsonDataWrapper {
        private List<QuestionSaveReq> list;

        public List<QuestionSaveReq> getList() {
            return list;
        }

        public void setList(List<QuestionSaveReq> list) {
            this.list = list;
        }
    }

    @Test
    public void testCreateQuestionSet() {
        //{"questionSetName":"评测数据集",
        // "extractConf":{"forwardConf":{"randomConf":{"randomCount":"2000"}},"negativeConf":{"randomConf":{"randomCount":"2000"}}},"questionCategory":["FORWARD","NEGATIVE"]}
        QuestionSetSaveReq req = new QuestionSetSaveReq();
        ExtractConfRandom forRandom = new ExtractConfRandom();
        forRandom.setRandomCount(2000);
        ExtractConfRandom negRandom = new ExtractConfRandom();
        negRandom.setRandomCount(2000);
        req.setQuestionSetName("评测数据集");
        req.setQuestionCategory(Arrays.asList("FORWARD", "NEGATIVE"));
        req.setExtractConf(ExtractConf.builder()
                .forwardConf(ForwardConf.builder()
                        .randomConf(forRandom)
                        .build())
                .negativeConf(NegativeConf.builder()
                        .randomConf(negRandom)
                        .build())
                .build());
        QuestionSetInfoDO questionSetInfoDO = questionSetService.create(req);
        log.info("{}", questionSetInfoDO);
    }

    /**
     * 新增大模型
     */
    @Test
    public void testCreateModel(){
        ModelInfoSaveReq modelInfo = ModelInfoSaveReq.builder()
                .useScript(false)
                .modelName("deepseek-32b")
                .modelVersion("v1.0")
                .modelPath("/aipaas/lm/v1/ds/ds32")
                .modelReq("deepseek-r1:32b")
                .modelDescribe("DeepSeek-32B")
                .originName("eop-auth-v1")
                .modelUrl("https://10.37.69.190:10443")
                .appid("aaa")
                .apikeys("aaaaaa")
                .maxThreadSize(8)
                .modelHandler("deepseek")
                .appName("DeepSeek-32B")
                .maxCompletionTokens(1500)
                .stream(true)
                .build();

        modelInfoService.createModelInfo(modelInfo);
    }

    @Test
    public void testCreatTask(){
//        {"name":"G470","description":"111111","handlerParam":"{\"modelId\":[26],\"questionSet\":[27]}","runType":"0","cronExpression":null,"oneTimeExpression":null,"maxThreadSize":1}
        JobSaveReq req = JobSaveReq.builder()
                .name("测评任务")
                .handlerParam("{\"modelId\":[26],\"questionSet\":[27]}")
                .runType(0)
                .cronExpression(null)
                .oneTimeExpression(null)
                .maxThreadSize(1)
                .build();
        try {
            jobService.createJob(req);
        } catch (SchedulerException e) {
            throw new RuntimeException(e);
        }
    }
}
