package com.chinatelecom.aigc.evaluate.handler;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.chinatelecom.aigc.evaluate.common.util.crypto.AESUtil;
import com.chinatelecom.aigc.evaluate.common.util.sign.SignUtils;
import com.chinatelecom.aigc.evaluate.common.util.threads.ThreadPoolUtils;
import com.chinatelecom.aigc.evaluate.domain.JobDO;
import com.chinatelecom.aigc.evaluate.domain.ModelInfoDO;
import com.chinatelecom.aigc.evaluate.domain.QuestionSetInfoDO;
import com.chinatelecom.aigc.evaluate.dto.model.TaskMessage;
import com.chinatelecom.aigc.evaluate.dto.resp.JobResp;
import com.chinatelecom.aigc.evaluate.dto.resp.QuestionSetItemResp;
import com.chinatelecom.aigc.evaluate.job.core.util.ScriptExecutorUtils;
import com.chinatelecom.aigc.evaluate.job.enums.job.JobStatusEnum;
import com.chinatelecom.aigc.evaluate.mapper.JobMapper;
import com.chinatelecom.aigc.evaluate.mapper.ModelInfoMapper;
import com.chinatelecom.aigc.evaluate.service.QuestionSetItemService;
import com.chinatelecom.aigc.evaluate.service.TaskAnswerService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.stream.Collectors;

import static com.chinatelecom.aigc.evaluate.common.exception.enums.ErrorCodeConstants.MODEL_APIKEY_ENCRYPT_ERROR;
import static com.chinatelecom.aigc.evaluate.common.exception.enums.ErrorCodeConstants.MODEL_NOT_EXISTS_ERROR;
import static com.chinatelecom.aigc.evaluate.common.exception.util.ServiceExceptionUtil.exception;

@Slf4j
@Component
public class ExecuteJobHandler {

    public static final String DEEPSEEK = "deepseek";
    public static final String XINGHE = "xinghe";
    private final JobMapper jobMapper;
    private final ModelInfoMapper modelInfoMapper;
    private final ThreadPoolUtils threadPoolUtils;
    private final QuestionSetItemService questionSetItemService;
    private final TaskAnswerService taskAnswerService;
    private final ScriptExecutorUtils scriptExecutorUtils;
    private final KafkaTemplate<String, TaskMessage> kafkaTemplate;


    public ExecuteJobHandler(JobMapper jobMapper,
                             ModelInfoMapper modelInfoMapper,
                             ThreadPoolUtils threadPoolUtils,
                             QuestionSetItemService questionSetItemService,
                             TaskAnswerService taskAnswerService,
                             ScriptExecutorUtils scriptExecutorUtils,
                             KafkaTemplate<String, TaskMessage> kafkaTemplate) {
        this.jobMapper = jobMapper;
        this.modelInfoMapper = modelInfoMapper;
        this.threadPoolUtils = threadPoolUtils;
        this.questionSetItemService = questionSetItemService;
        this.taskAnswerService = taskAnswerService;
        this.scriptExecutorUtils = scriptExecutorUtils;
        this.kafkaTemplate = kafkaTemplate;
    }

    public void execute(JobResp jobResp) {
        log.info("开始执行execute,params:{}", jobResp);
        // 任务ID
        Long taskId = jobResp.getId();
        // 最大线程数量
        Integer maxThreadSize = jobResp.getMaxThreadSize();
        // 更新任务执行时间
        updateJobStartTime(taskId, LocalDateTime.now());

        List<CompletableFuture<Void>> futures = new ArrayList<>();

        // 获取执行的模型集合
        List<ModelInfoDO> modelInfoSet = jobResp.getModelInfoSet();
        log.info("获取执行的模型:{}", modelInfoSet);

        // 遍历执行模型评测任务
        modelInfoSet.forEach(model -> {
            Long modelId = model.getId();
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    ModelInfoDO modelInfo = validateModelInfoExists(modelId);
                    if (modelInfo == null) {
                        return;
                    }
                    // 执行流程
                    log.info("开始执行模型流程:{}", modelInfo);
                    processModel(modelInfo, jobResp, taskId, maxThreadSize);
                } catch (Exception e) {
                    log.error("任务:{} 模型:{} 执行失败 {}", taskId, modelId, e);
                    updateJobStatus(taskId, JobStatusEnum.COMPLETE);
                    throw new RuntimeException("模型执行失败", e);
                }
            });
            futures.add(future);
        });
    }

    public void execute1(JobResp jobResp) {
        log.info("开始执行execute,params:{}", jobResp);
        // 任务ID
        Long taskId = jobResp.getId();
        // 最大线程数量
        Integer maxThreadSize = jobResp.getMaxThreadSize();
        // 更新任务执行时间
        updateJobStartTime(taskId, LocalDateTime.now());

        // 获取执行的模型集合
        List<ModelInfoDO> modelInfoSet = jobResp.getModelInfoSet();
        log.info("获取执行的模型:{}", modelInfoSet);

        // 遍历执行模型评测任务
        modelInfoSet.forEach(model -> {
            Long modelId = model.getId();
            try {
                ModelInfoDO modelInfo = validateModelInfoExists(modelId);
                if (modelInfo == null) {
                    return;
                }
                // 执行流程
                log.info("开始执行模型流程:{}", modelInfo);
                processModel1(modelInfo, jobResp, taskId, maxThreadSize);
            } catch (Exception e) {
                log.error("任务:{} 模型:{} 执行失败 {}", taskId, modelId, e);
                updateJobStatus(taskId, JobStatusEnum.COMPLETE);
                throw new RuntimeException("模型执行失败", e);
            }
        });
    }

    /**
     * 消息队列版本
     * @param jobResp
     */
    public void executeMsg(JobResp jobResp) {
        log.info("开始执行executeMsg,params:{}", jobResp);
        // 任务ID
        Long taskId = jobResp.getId();
        // 最大线程数量
        Integer maxThreadSize = jobResp.getMaxThreadSize();
        // 更新任务执行时间
        updateJobStartTime(taskId, LocalDateTime.now());

        // 获取执行的模型集合
        List<ModelInfoDO> modelInfoSet = jobResp.getModelInfoSet();
        log.info("获取执行的模型:{}", modelInfoSet);

        // 遍历执行模型评测任务
        modelInfoSet.forEach(model -> {
            Long modelId = model.getId();
            try {
                ModelInfoDO modelInfo = validateModelInfoExists(modelId);
                if (modelInfo == null) {
                    return;
                }
                // 执行流程
                log.info("开始执行模型流程-Msg :{}", modelInfo);
                processModelMsg(modelInfo, jobResp, taskId, maxThreadSize);
            } catch (Exception e) {
                log.error("任务:{} 模型:{} 执行失败 {}", taskId, modelId, e);
                updateJobStatus(taskId, JobStatusEnum.COMPLETE);
                throw new RuntimeException("模型执行失败", e);
            }
        });
    }

    /**
     * 更新任务时间
     * @param taskId 任务ID
     * @param startTime 时间戳
     */
    private void updateJobStartTime(Long taskId, LocalDateTime startTime) {
        // 创建更新对象
        JobDO updateObj = JobDO.builder()
                .id(taskId)
                .status(JobStatusEnum.EXECUTING.getStatus())
                .startTime(startTime)  // 设置开始时间
                .endTime(null)
                .build();

        // 打印日志，记录任务更新的信息
        log.info("更新任务开始时间: taskId={}, startTime={}", taskId, startTime);

        // 执行更新操作
        jobMapper.updateById(updateObj);
    }

    private void updateJobStatus(Long taskId, JobStatusEnum status) {
        JobDO updateObj = JobDO.builder().id(taskId).status(status.getStatus()).build();
        log.info("更新任务状态: {}", updateObj);
        jobMapper.updateById(updateObj);
    }


    private ModelInfoDO validateModelInfoExists(Long id) {
        ModelInfoDO modelInfo = modelInfoMapper.selectById(id);
        if (modelInfo == null) {
            throw exception(MODEL_NOT_EXISTS_ERROR);
        }

        try {
            Boolean useScript = modelInfo.getUseScript();
            if (!useScript) {
//                modelInfo.setApikeys(AESUtil.decrypt(modelInfo.getApikeys())); // API Key 解密
            }
        } catch (Exception e) {
            throw exception(MODEL_APIKEY_ENCRYPT_ERROR);
        }

        return modelInfo;
    }


    private void processModel(ModelInfoDO modelInfo, JobResp jobResp, Long taskId, int maxThreadSize) throws Exception {
        List<Long> questionSet = jobResp.getQuestionSet().stream().map(QuestionSetInfoDO::getId).collect(Collectors.toList());
        if (questionSet.isEmpty()) {
            throw new IllegalArgumentException("handlerParam 缺少 questionSet 字段");
        }

        String taskName = modelInfo.getAppName() + modelInfo.getModelName() + modelInfo.getModelVersion();
        ThreadPoolExecutor executor = threadPoolUtils.getOrCreatePool(taskName, modelInfo.getMaxThreadSize(), modelInfo.getMaxThreadSize(), 60, 100);

        threadPoolUtils.setGlobalLimit(taskId, maxThreadSize);
        Semaphore semaphore = threadPoolUtils.getDomainSemaphore(taskId);

        List<QuestionSetItemResp> lstQuestionSet = questionSetItemService.list(questionSet, false);
        log.info("查询到 {} 条题目信息", lstQuestionSet.size());

        if (!lstQuestionSet.isEmpty()) {
            CountDownLatch latch = new CountDownLatch(lstQuestionSet.size());

            Boolean useScript = modelInfo.getUseScript();
            String scriptLanguage = modelInfo.getScriptLanguage();
            String scriptSource = modelInfo.getScriptSource();
            if (useScript) {
                log.info("Script任务 : {}", taskId);
            } else {
                log.info("正常任务 : {}", taskId);
            }

            for (QuestionSetItemResp question : lstQuestionSet) {
                // 启动新的线程处理任务
                executor.execute(() -> {
                    try {
                        // 如果有脚本，执行脚本
                        if (useScript) {
                            handleScriptTask(
                                    question,
                                    modelInfo,
                                    taskId,
                                    latch,
                                    semaphore,
                                    question.getQuestionSetId(),
                                    scriptLanguage,
                                    scriptSource
                            );
                        } else {
//                            String authorization = makeSign(modelInfo);
                            // 正常的 handleTask
                            handleTask(question, modelInfo, null, taskId, latch, semaphore, question.getQuestionSetId());
                        }

                    } catch (Exception e) {
                        log.error("处理题目失败: {}", e.getMessage(), e);
                    }
                });
            }

            awaitLatchCompletion(latch, taskId, modelInfo.getId());

            // 当前模型判断
            log.info("当前模型判断评审: {}", modelInfo);
            taskAnswerService.autoJudgment(taskId, modelInfo.getId());
        }
    }


    private void processModel1(ModelInfoDO modelInfo, JobResp jobResp, Long taskId, int maxThreadSize) throws Exception {
        List<Long> questionSet = jobResp.getQuestionSet().stream().map(QuestionSetInfoDO::getId).collect(Collectors.toList());
        if (questionSet.isEmpty()) {
            throw new IllegalArgumentException("handlerParam 缺少 questionSet 字段");
        }

        String taskName = modelInfo.getAppName() + modelInfo.getModelName() + modelInfo.getModelVersion();
        ThreadPoolExecutor executor = threadPoolUtils.getOrCreatePool(taskName, modelInfo.getMaxThreadSize(), modelInfo.getMaxThreadSize(), 60, 100);

        threadPoolUtils.setGlobalLimit(taskId, maxThreadSize);
        Semaphore semaphore = threadPoolUtils.getDomainSemaphore(taskId);

        List<QuestionSetItemResp> lstQuestionSet = questionSetItemService.list(questionSet, false);
        log.info("查询到 {} 条题目信息", lstQuestionSet.size());

        if (!lstQuestionSet.isEmpty()) {
            CountDownLatch latch = new CountDownLatch(lstQuestionSet.size());

            Boolean useScript = modelInfo.getUseScript();
            String scriptLanguage = modelInfo.getScriptLanguage();
            String scriptSource = modelInfo.getScriptSource();
            if (useScript) {
                log.info("Script任务 : {}", taskId);
            } else {
                log.info("正常任务 : {}", taskId);
            }

            for (QuestionSetItemResp question : lstQuestionSet) {
                try {
                    // 如果有脚本，执行脚本
                    if (useScript) {
                        handleScriptTask(
                                question,
                                modelInfo,
                                taskId,
                                latch,
                                semaphore,
                                question.getQuestionSetId(),
                                scriptLanguage,
                                scriptSource
                        );
                    } else {
//                            String authorization = makeSign(modelInfo);
                        // 正常的 handleTask
                        handleTask1(question, modelInfo, null, taskId, latch, semaphore, question.getQuestionSetId());
                    }

                } catch (Exception e) {
                    log.error("处理题目失败: {}", e.getMessage(), e);
                }
            }

            // 阻塞 直到所有题目执行完成
            awaitLatchCompletion(latch, taskId, modelInfo.getId());

            // 当前模型判断
            log.info("当前模型判断评审: {}", modelInfo);
            taskAnswerService.autoJudgment(taskId, modelInfo.getId());
        }
    }

    private void processModelMsg(ModelInfoDO modelInfo, JobResp jobResp, Long taskId, int maxThreadSize) throws Exception {
        List<Long> questionSet = jobResp.getQuestionSet().stream().map(QuestionSetInfoDO::getId).collect(Collectors.toList());
        if (questionSet.isEmpty()) {
            throw new IllegalArgumentException("handlerParam 缺少 questionSet 字段");
        }
        List<QuestionSetItemResp> lstQuestionSet = questionSetItemService.list(questionSet, false);
        log.info("查询到 {} 条题目信息", lstQuestionSet.size());

        if (!lstQuestionSet.isEmpty()) {
            if (modelInfo.getUseScript()) {
                log.info("Script任务 : {}", taskId);
            } else {
                log.info("正常任务 : {}", taskId);
            }
            String topic = "ai-evaluation-task-" + taskId + "-" + modelInfo.getId();
            log.info("当前队列主题信息 🆕: {}", topic);
            // 发送消息到Kafka
            for (QuestionSetItemResp question : lstQuestionSet) {
                kafkaTemplate.send(topic, String.valueOf(question.getQuestionId()), TaskMessage.builder()
                        .taskId(taskId)
                        .modelInfo(modelInfo)
                        .jobResp(jobResp)
                        .question(question)
                        .createDateTime(LocalDateTime.now())
                        .build());
            }
            // 当前模型判断
            log.info("当前模型判断评审: {}", modelInfo);
            taskAnswerService.autoJudgment(taskId, modelInfo.getId());
        }
    }

    private void handleScriptTask(
            QuestionSetItemResp question,
            ModelInfoDO modelInfo,
            Long taskId,
            CountDownLatch latch,
            Semaphore semaphore,
            Long questionSet,
            String scriptLanguage,
            String scriptSource
    ) {
        try {
            semaphore.acquire(); // 限流控制
            JobStatusEnum status = getJobStatus(taskId);
            if (!JobStatusEnum.PAUSED.equals(status)) {
                long threadId = Thread.currentThread().getId();
                String threadName = Thread.currentThread().getName();
                log.debug("【Script任务】线程 ID: {}, 线程名称: {}", threadId, threadName);

                Map<String, Object> bindings = new HashMap<>();
                bindings.put("taskId", taskId);
                bindings.put("question", question.getTitle());

                Object answer = scriptExecutorUtils.runScript(scriptLanguage, scriptSource, bindings);
                String answerStr = (answer != null) ? answer.toString() : "";

                taskAnswerService.createDo(
                        taskId,
                        modelInfo.getId(),
                        question.getQuestionId(),
                        question.getQuestionVersion(),
                        modelInfo.getAppName(),
                        modelInfo.getModelName(),
                        modelInfo.getModelVersion(),
                        question.getTitle(),
                        answerStr,
                        question.getCategory(),
                        0,
                        questionSet
                );

                log.debug("【Script任务】taskId: {} 模型: {} 脚本执行完成，题目ID: {}, answer: {}", taskId, modelInfo.getModelName() + ":" + modelInfo.getModelVersion(), question.getQuestionId(), answerStr);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();  // 恢复中断状态
            log.error("【Script任务】处理题目 {} 时被中断", question.getQuestionId(), e);
        } catch (Exception e) {
            log.error("【Script任务】处理题目 {} 时执行脚本发生异常", question.getQuestionId(), e);
        } finally {
            semaphore.release(); // 释放限流许可
            if (latch.getCount() > 0) {
                latch.countDown();
            }
        }
    }


    private String makeSign(ModelInfoDO modelInfo) {
        long timestamp = System.currentTimeMillis() / 1000L;
        Map<String, String> headerMap = new HashMap<>();
        String signedheaders = modelInfo.getAppid();
        headerMap.put("X-APP-ID", modelInfo.getAppid());
        //根据文档进行替换
        String basic = modelInfo.getOriginName() + "/" + modelInfo.getAppid() + "/" + "QG" + "/" + timestamp + "/180000";
        Map<String, String> params = new HashMap<>();
        String sign = SignUtils.genSignature(
                basic,
                //X-APP-KEY
                modelInfo.getApikeys(),
                //请求的发送方式   POST GET 等 websocket 是GET
                HttpMethod.POST,
                //请求路径的path
                modelInfo.getModelPath(),
                params,
                headerMap,
                signedheaders);

        //生成的就是 Authorization 注意生成的有斜杠进行拼接
        //String signResult = String.format("Authorization: %s/%s/%s", basic, signedheaders, sign);
        String authorization = String.format("%s/%s/%s", basic, signedheaders, sign);
        log.debug("authorization: {}", authorization);

        return authorization;
    }

    private void handleTask(QuestionSetItemResp question, ModelInfoDO modelInfo, String authorization,
                            Long taskId, CountDownLatch latch, Semaphore semaphore, Long questionSet) {
        try {
            semaphore.acquire(); // 限流控制
            JobStatusEnum status = getJobStatus(taskId);
            if (!JobStatusEnum.PAUSED.equals(status)) {
                long threadId = Thread.currentThread().getId();
                String threadName = Thread.currentThread().getName();
                log.debug("任务线程 ID: {}, 线程名称: {}", threadId, threadName);

                String modelAnswer = getModelAnswer(modelInfo, authorization, question.getTitle());
                if (modelAnswer != null && !modelAnswer.isEmpty()) {

                } else {
                    modelAnswer = "";
                }
                log.debug("taskId: {} 模型: {} 题目: {} 的答案: {}", taskId, modelInfo.getModelName() + ":" + modelInfo.getModelVersion(), question.getQuestionId(), modelAnswer);

                taskAnswerService.createDo(
                        taskId,
                        modelInfo.getId(),
                        question.getQuestionId(),
                        question.getQuestionVersion(),
                        modelInfo.getAppName(),
                        modelInfo.getModelName(),
                        modelInfo.getModelVersion(),
                        question.getTitle(),
                        modelAnswer,
                        question.getCategory(),
                        0,
                        questionSet
                );
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();  // 恢复中断状态
            log.error("处理题目 {} 时被中断", question.getQuestionId(), e);
        } catch (Exception e) {
            log.error("处理题目 {} 时发生异常", question.getQuestionId(), e);
        } finally {
            semaphore.release(); // 释放限流许可
            if (latch.getCount() > 0) {
                latch.countDown();
            }
        }
    }

    private void handleTask1(QuestionSetItemResp question, ModelInfoDO modelInfo, String authorization,
                             Long taskId, CountDownLatch latch, Semaphore semaphore, Long questionSet) {
        try {
            semaphore.acquire(); // 限流控制
            JobStatusEnum status = getJobStatus(taskId);
            if (!JobStatusEnum.PAUSED.equals(status)) {
                long threadId = Thread.currentThread().getId();
                String threadName = Thread.currentThread().getName();
                log.info("任务线程 ID: {}, 线程名称: {}", threadId, threadName);

                // 获取 大模型输出
                String modelAnswer = getModelAnswer1(modelInfo, authorization, question.getTitle());
                if (modelAnswer != null && !modelAnswer.isEmpty()) {

                } else {
                    modelAnswer = "";
                }
                log.info("taskId: {} 模型: {} 题目: {} 的答案: {}", taskId, modelInfo.getModelName() + ":" + modelInfo.getModelVersion(), question.getQuestionId(), modelAnswer);

                taskAnswerService.createDo(
                        taskId,
                        modelInfo.getId(),
                        question.getQuestionId(),
                        question.getQuestionVersion(),
                        modelInfo.getAppName(),
                        modelInfo.getModelName(),
                        modelInfo.getModelVersion(),
                        question.getTitle(),
                        modelAnswer,
                        question.getCategory(),
                        0,
                        questionSet
                );
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();  // 恢复中断状态
            log.error("处理题目 {} 时被中断", question.getQuestionId(), e);
        } catch (Exception e) {
            log.error("处理题目 {} 时发生异常", question.getQuestionId(), e);
        } finally {
            semaphore.release(); // 释放限流许可
            if (latch.getCount() > 0) {
                latch.countDown();
            }
        }
    }

    private void awaitLatchCompletion(CountDownLatch latch, Long taskId, Long modelId) {
        try {
            latch.await(); // 阻塞直到所有任务完成
            log.info("任务:{} 模型:{} 执行完成，开始执行后续操作...", taskId, modelId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("任务:{} 模型:{} 执行失败", taskId, modelId, e);
            throw new RuntimeException("任务执行失败", e);
        }
    }

    private JobStatusEnum getJobStatus(Long taskId) {
        // 假设 jobMapper 提供了一个根据 taskId 查询任务状态的方法
        JobDO job = jobMapper.selectById(taskId);
        if (job == null) {
            return JobStatusEnum.FAILED;
        }
        JobStatusEnum status = JobStatusEnum.fromStatus(job.getStatus());
        return status;
    }

    private String getModelAnswer(ModelInfoDO modelInfo, String authorization, String content) {
        try {
            // 设置请求头，用于公网调用鉴权
            Map<String, String> headers = new HashMap<>();
            headers.put("X-APP-ID", modelInfo.getAppid());
            headers.put("Authorization", authorization);
            headers.put("Content-Type", "application/json");
            headers.put("Accept", "*/*");

            // 创建请求对象
            Map<String, Object> request = new HashMap<>();
            request.put("model", modelInfo.getModelReq());
            Map<String, Object> messages = new HashMap<>();
            messages.put("role", "user");
            messages.put("content", content);
            request.put("messages", Arrays.asList(messages));
            request.put("max_completion_tokens", modelInfo.getMaxCompletionTokens());
            //request.put("max_tokens", 6092);

            boolean isStream = modelInfo.getStream();
            request.put("stream", isStream);

            if (isStream) {
                Map<String, Object> stream = new HashMap<>();
                stream.put("include_usage", true);
                request.put("stream_options", stream);
            }

            // 将请求对象转换为 JsonNode
            String requestString = JSONUtil.toJsonStr(request);
            String modelUrl = modelInfo.getModelUrl() + modelInfo.getModelPath();

            // 发起 HTTP 请求
            HttpResponse response = HttpRequest.post(modelUrl)
                    .headerMap(headers, true)
                    .body(requestString)
                    .execute();

            // 如果是流模式（isStream == true）
            if (isStream) {
                StringBuilder mergedResponse = new StringBuilder();
                InputStream inputStream = response.bodyStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                String line;
                String lastLine = "";

                try {
                    while ((line = reader.readLine()) != null) {
                        lastLine = line;
                        if (line.startsWith("data:")) {
                            String jsonStr = line.substring(5).trim();
                            if (jsonStr.equals("[DONE]")) {
                                break;
                            }

                            try {
                                JSONObject jsonObj = JSON.parseObject(jsonStr);
                                if (jsonObj.containsKey("choices")) {
                                    JSONArray choices = jsonObj.getJSONArray("choices");
                                    for (int i = 0; i < choices.size(); i++) {
                                        JSONObject choice = choices.getJSONObject(i);
                                        JSONObject delta = choice.getJSONObject("delta");
                                        if (delta.containsKey("content")) {
                                            String answerContent = delta.getString("content");
                                            if (answerContent != null && !answerContent.isEmpty()) {
                                                mergedResponse.append(answerContent);
                                            }
                                        }
                                    }
                                } else if (jsonObj.containsKey("message")) {
                                    // 处理错误消息
                                    mergedResponse.append(jsonObj.getString("message"));
                                }
                            } catch (Exception e) {
                                // 解析 JSON 失败，跳过这行数据
                                continue;
                            }
                        }
                    }
                } finally {
                    reader.close();
                }

                if (mergedResponse.toString().equals("")) {
                    return lastLine;
                }
                // 返回最终合并后的响应内容
                return mergedResponse.toString();
            } else {
                // 如果不是流模式（isStream == false），直接返回响应体内容
                if (response.getStatus() == 200) {
                    return parseModelAnswer(response.body()); // 根据需要解析响应内容
                } else {
                    return response.body(); // 如果状态码不是 200，直接返回响应内容
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
            return null;  // 如果发生异常，返回 null
        }
    }

    private String getModelAnswer1(ModelInfoDO modelInfo, String authorization, String content) {
        return "当然！女性怀孕是一个奇妙而复杂的过程，为了迎接新生命的诞生，母体的几乎每一个系统都会发生一系列显著的变化。这些变化是为了适应胎儿的生长发育，并为分娩和哺乳做准备。\n" +
                "\n" +
                "以下是怀孕期间女性身体发生的主要变化，分为几个方面：\n" +
                "\n" +
                "### 一、生殖系统与乳房的显著变化\n" +
                "\n" +
                "这是最直接相关的系统，变化也最为明显。\n" +
                "\n" +
                "*   **子宫：**\n" +
                "    *   **大小与形状：** 子宫从怀孕前像一个倒置的小梨（约50克），扩展到足月时像一个巨大的西瓜（约1000克）。容量增加约500-1000倍。\n" +
                "    *   **血流：** 子宫血流量大幅增加，以满足胎盘和胎儿的需求。\n" +
                "*   **宫颈：** 变得更软、颜色变深（称为古德尔氏征），并会形成粘液栓，封闭宫颈口，防止细菌进入，保护胎儿。\n" +
                "*   **卵巢：** 排卵停止，卵巢主要分泌孕激素以维持妊娠。\n" +
                "*   **阴道与会阴：** 阴道黏膜增厚，分泌物增多（白带）。会阴部血流增加，组织变软，为分娩时的扩张做准备。\n" +
                "*   **乳房：**\n" +
                "    *   **增大与胀痛：** 在雌激素和孕激素的作用下，乳腺管和腺泡增生，乳房明显增大，可能伴有胀痛感。\n" +
                "    *   **颜色变深：** 乳晕和乳头颜色变深，乳晕上的小腺体（蒙氏结节）变得突出。\n" +
                "    *   **泌乳准备：** 孕中期后，有些孕妇可能会分泌少量淡黄色液体，称为初乳。\n" +
                "\n" +
                "### 二、心血管与血液循环系统\n" +
                "\n" +
                "身体需要为两个人泵血，心脏工作量大大增加。\n" +
                "\n" +
                "*   **血容量增加：** 血容量增加约40-50%，导致心脏负荷加重。\n" +
                "*   **心率加快：** 心率每分钟大约增加10-15次，以确保足够的血液输送。\n" +
                "*   **血压变化：** 孕早期和中期血压可能略有下降，孕晚期恢复正常。需要密切监测妊娠期高血压疾病。\n" +
                "*   **贫血倾向：** 血容量增加主要是血浆，红细胞的增加速度跟不上，导致血液相对“稀释”，出现生理性贫血。\n" +
                "*   **静脉回流受阻：** 增大的子宫压迫下腔静脉，可能导致：\n" +
                "    *   **下肢水肿：** 脚踝和腿部肿胀。\n" +
                "    *   **静脉曲张：** 腿部或肛门（痔疮）出现静脉曲张。\n" +
                "    *   **仰卧位低血压：** 平躺时子宫压迫大血管，可能引起头晕，建议侧卧。\n" +
                "\n" +
                "### 三、呼吸系统\n" +
                "\n" +
                "对氧气的需求增加。\n" +
                "\n" +
                "*   **呼吸方式改变：** 孕激素刺激呼吸中枢，使呼吸变得稍快且深。\n" +
                "*   **轻微气短：** 子宫上推膈肌，使肺部活动空间变小，尤其在孕晚期，可能感到轻微气短。\n" +
                "\n" +
                "### 四、消化系统\n" +
                "\n" +
                "变化多样，常导致各种不适。\n" +
                "\n" +
                "*   **恶心呕吐（孕吐）：** 主要由孕早期激素水平急剧变化引起，通常在孕中期缓解。\n" +
                "*   **口味改变与食欲波动：** 可能特别想吃某种食物（俗称“害口”），或对某些气味异常敏感。\n" +
                "*   **胃灼热（烧心）和消化不良：** 孕激素使胃肠道平滑肌松弛，胃排空减慢，贲门括约肌松弛，胃酸易反流。\n" +
                "*   **便秘：** 肠道蠕动减慢，加上子宫压迫直肠，容易导致便秘。\n" +
                "*   **牙龈出血：** 激素变化使牙龈充血水肿，更容易出血。\n" +
                "\n" +
                "### 五、泌尿系统\n" +
                "\n" +
                "代谢废物增加，需要更高效地过滤。\n" +
                "\n" +
                "*   **尿频：** 孕早期和晚期，增大的子宫压迫膀胱，导致尿频。\n" +
                "*   **肾血流量增加：** 肾脏过滤功能增强，但这也可能使糖分（尿糖）更易漏出，不一定代表有问题。\n" +
                "\n" +
                "### 六、皮肤、毛发与骨骼\n" +
                "\n" +
                "*   **皮肤：**\n" +
                "    *   **色素沉着：** 乳头、乳晕、外阴、腹中线（妊娠线）颜色加深。面部可能出现黄褐斑（“妊娠斑”）。\n" +
                "    *   **妊娠纹：** 皮肤因快速拉伸，弹性纤维断裂，在腹部、乳房、大腿等处出现粉色或紫色的条纹。\n" +
                "*   **毛发：** 孕期毛发脱落减少，显得浓密。产后激素水平下降，会出现明显的脱发期（休止期脱发）。\n" +
                "*   **骨骼与关节：**\n" +
                "    *   **骨盆松弛：** 松弛素激素使韧带松弛，特别是骨盆关节，为分娩做准备，但可能增加关节不稳定的风险。\n" +
                "    *   **姿势改变：** 重心前移，导致腰椎前凸，可能引起腰背痛。\n" +
                "\n" +
                "### 七、内分泌与代谢系统\n" +
                "\n" +
                "这是驱动所有变化的“总指挥部”。\n" +
                "\n" +
                "*   **激素水平剧变：** 人体绒毛膜促性腺激素（hCG）、雌激素、孕激素、松弛素、催乳素等激素水平显著升高，主导了孕期的各种生理变化。\n" +
                "*   **新陈代谢加快：** 基础代谢率增加，对热量和营养的需求增高。\n" +
                "*   **体重增加：** 健康的体重增加对胎儿发育至关重要，通常建议增加11-16公斤（根据孕前体重指数而定）。\n" +
                "\n" +
                "### 总结与重要提示\n" +
                "\n" +
                "这些变化是怀孕的正常生理过程，但有时也会带来不适。每位女性的体验都是独特的，程度各不相同。\n" +
                "\n" +
                "**最重要的是：** 定期进行产前检查，让医生监测您和胎儿的健康状况。如果出现任何让您担心的症状，如剧烈呕吐、严重腹痛、阴道出血、严重头痛或视力模糊等，请立即就医。\n" +
                "\n" +
                "怀孕是一段充满挑战却又无比神奇的旅程，了解这些变化能帮助准妈妈们更好地照顾自己，迎接新生命的到来。";
        //        try {
//            // 设置请求头，用于公网调用鉴权
//            Map<String, String> headers = new HashMap<>();
//            headers.put("Authorization", String.format("Bearer %s", modelInfo.getApikeys()));
//            headers.put("Content-Type", "application/json");
//
//            // 创建请求对象
//            Map<String, Object> request = new HashMap<>();
//            request.put("model", "deepseek-chat");
//            Map<String, Object> messages = new HashMap<>();
//            messages.put("role", "user");
//            messages.put("content", content);
//            request.put("messages", Arrays.asList(messages));
//            request.put("max_completion_tokens", modelInfo.getMaxCompletionTokens());
//            //request.put("max_tokens", 6092);
//
//            boolean isStream = modelInfo.getStream();
//            request.put("stream", isStream);
//
//            if (isStream) {
//                Map<String, Object> stream = new HashMap<>();
//                stream.put("include_usage", true);
//                request.put("stream_options", stream);
//            }
//
//            // 将请求对象转换为 JsonNode
//            String requestString = JSONUtil.toJsonStr(request);
//            String modelUrl = modelInfo.getModelUrl() + modelInfo.getModelPath();
//
//            // 发起 HTTP 请求
//            HttpResponse response = HttpRequest.post(modelUrl)
//                    .headerMap(headers, true)
//                    .body(requestString)
//                    .execute();
//
//            // 如果是流模式（isStream == true）
//            if (isStream) {
//                StringBuilder mergedResponse = new StringBuilder();
//                InputStream inputStream = response.bodyStream();
//                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
//                String line;
//                String lastLine = "";
//
//                try {
//                    while ((line = reader.readLine()) != null) {
//                        lastLine = line;
//                        if (line.startsWith("data:")) {
//                            String jsonStr = line.substring(5).trim();
//                            if (jsonStr.equals("[DONE]")) {
//                                break;
//                            }
//
//                            try {
//                                JSONObject jsonObj = JSON.parseObject(jsonStr);
//                                if (jsonObj.containsKey("choices")) {
//                                    JSONArray choices = jsonObj.getJSONArray("choices");
//                                    for (int i = 0; i < choices.size(); i++) {
//                                        JSONObject choice = choices.getJSONObject(i);
//                                        JSONObject delta = choice.getJSONObject("delta");
//                                        if (delta.containsKey("content")) {
//                                            String answerContent = delta.getString("content");
//                                            if (answerContent != null && !answerContent.isEmpty()) {
//                                                mergedResponse.append(answerContent);
//                                            }
//                                        }
//                                    }
//                                } else if (jsonObj.containsKey("message")) {
//                                    // 处理错误消息
//                                    mergedResponse.append(jsonObj.getString("message"));
//                                }
//                            } catch (Exception e) {
//                                // 解析 JSON 失败，跳过这行数据
//                                continue;
//                            }
//                        }
//                    }
//                } finally {
//                    reader.close();
//                }
//
//                if (mergedResponse.toString().equals("")) {
//                    return lastLine;
//                }
//                // 返回最终合并后的响应内容
//                return mergedResponse.toString();
//            } else {
//                // 如果不是流模式（isStream == false），直接返回响应体内容
//                if (response.getStatus() == 200) {
//                    return parseModelAnswer(response.body()); // 根据需要解析响应内容
//                } else {
//                    return response.body(); // 如果状态码不是 200，直接返回响应内容
//                }
//            }
//
//        } catch (Exception e) {
//            e.printStackTrace();
//            return null;  // 如果发生异常，返回 null
//        }
    }

    private String parseModelAnswer(String modelAnswer) throws JsonProcessingException {
        JsonNode rootNode = new ObjectMapper().readTree(modelAnswer);
        JsonNode choicesNode = rootNode.path("choices");
        if (choicesNode.isArray() && !choicesNode.isEmpty()) {
            JsonNode messageNode = choicesNode.get(0).path("message");
            return messageNode.path("content").asText("");
        }
        return modelAnswer;
    }
}
