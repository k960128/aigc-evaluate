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
import com.chinatelecom.aigc.evaluate.domain.*;
import com.chinatelecom.aigc.evaluate.dto.resp.QuestionSetItemResp;
import com.chinatelecom.aigc.evaluate.job.core.handler.JobHandler;
import com.chinatelecom.aigc.evaluate.job.core.util.ScriptExecutorUtils;
import com.chinatelecom.aigc.evaluate.job.enums.job.JobStatusEnum;
import com.chinatelecom.aigc.evaluate.mapper.*;
import com.chinatelecom.aigc.evaluate.service.QuestionSetItemService;
import com.chinatelecom.aigc.evaluate.service.TaskAnswerService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.script.Bindings;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.chinatelecom.aigc.evaluate.common.exception.enums.ErrorCodeConstants.*;
import static com.chinatelecom.aigc.evaluate.common.exception.util.ServiceExceptionUtil.exception;

@Slf4j
@Component
public class TaskJobHandler implements JobHandler {

    public static final String DEEPSEEK = "deepseek";
    public static final String XINGHE = "xinghe";

    private final ThreadPoolUtils threadPoolUtils;
    private final ModelInfoMapper modelInfoMapper;
    private final JobMapper jobMapper;
    private final TaskAnswerService taskAnswerService;
    private final QuestionSetItemService questionSetItemService;
    private final ScriptExecutorUtils scriptExecutorUtils;

    public TaskJobHandler(ModelInfoMapper modelInfoMapper,
                          JobMapper jobMapper,
                          TaskAnswerService taskAnswerService,
                          QuestionSetItemService questionSetItemService,
                          ThreadPoolUtils threadPoolUtils,
                          ScriptExecutorUtils scriptExecutorUtils) {
        this.modelInfoMapper = modelInfoMapper;
        this.jobMapper = jobMapper;
        this.taskAnswerService = taskAnswerService;
        this.questionSetItemService = questionSetItemService;
        this.threadPoolUtils = threadPoolUtils;
        this.scriptExecutorUtils = scriptExecutorUtils;
    }

    public String execute(String param) throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode jsonNode = objectMapper.readTree(param);

        Long taskId = validateRequiredField(jsonNode, "taskId");
        int maxThreadSize = Math.toIntExact(validateRequiredField(jsonNode, "maxThreadSize"));
        log.info("执行任务-{}.........{}", taskId, LocalDateTime.now());
        updateJobStartTime(taskId, LocalDateTime.now());

        updateJobStatus(taskId, JobStatusEnum.EXECUTING);
        taskAnswerService.batchDeleteByTaskId(taskId);

        List<Long> modelIds = extractFieldAsLongList(jsonNode, "modelId");
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        // 使用 CompletableFuture 并行执行每个 modelId
        for (Long modelId : modelIds) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    ModelInfoDO modelInfo = validateModelInfoExists(modelId);
                    if (modelInfo == null) {
                        return;
                    }

                    if (Objects.equals(modelInfo.getModelHandler(), XINGHE)) {
                        processModel(modelInfo, jsonNode, taskId, maxThreadSize);
                    } else if (Objects.equals(modelInfo.getModelHandler(), DEEPSEEK)) {
                        processModel(modelInfo, jsonNode, taskId, maxThreadSize);
                    } else {
                        processModel(modelInfo, jsonNode, taskId, maxThreadSize);
                    }
                } catch (Exception e) {
                    log.error("任务:{} 模型:{} 执行失败 {}", taskId, modelId, e);
                    updateJobStatus(taskId, JobStatusEnum.COMPLETE);
                    throw new RuntimeException("模型执行失败", e);
                }
            });
            futures.add(future);
        }

        // 等待所有模型的任务完成
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        updateJobStatus(taskId, JobStatusEnum.COMPLETE);
        updateJobEndTime(taskId, LocalDateTime.now());

        log.info("任务执行完成-{}.........{}", taskId, LocalDateTime.now());
        return String.format("执行完成,{%s}", LocalDateTime.now());
    }

    private void processModel(ModelInfoDO modelInfo, JsonNode jsonNode, Long taskId, int maxThreadSize) throws Exception {
        List<Long> questionSet = extractFieldAsLongList(jsonNode, "questionSet");
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
                            String authorization = makeSign(modelInfo);
                            // 正常的 handleTask
                            handleTask(question, modelInfo, authorization, taskId, latch, semaphore, question.getQuestionSetId());
                        }

                    } catch (Exception e) {
                        log.error("处理题目失败: {}", e.getMessage(), e);
                    }

                    //handleTask(question, modelInfo, authorization, taskId, latch, semaphore, questionSet.get(0));
                });
            }

            awaitLatchCompletion(latch, taskId, modelInfo.getId());

            taskAnswerService.autoJudgment(taskId, modelInfo.getId());
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

    private void updateJobStatus(Long taskId, JobStatusEnum status) {
        JobDO updateObj = JobDO.builder().id(taskId).status(status.getStatus()).build();
        log.info("更新任务状态: {}", updateObj);
        jobMapper.updateById(updateObj);
    }

    private void updateJobStartTime(Long taskId, LocalDateTime startTime) {
        // 创建更新对象
        JobDO updateObj = JobDO.builder()
                .id(taskId)
                .startTime(startTime)  // 设置开始时间
                .endTime(null)
                .build();

        // 打印日志，记录任务更新的信息
        log.info("更新任务开始时间: taskId={}, startTime={}", taskId, startTime);

        // 执行更新操作
        jobMapper.updateById(updateObj);
    }

    private void updateJobEndTime(Long taskId, LocalDateTime endTime) {
        JobDO updateObj = JobDO.builder()
                .id(taskId)
                .endTime(endTime)  // 设置结束时间
                .build();

        // 打印日志，记录任务结束时间更新的信息
        log.info("更新任务结束时间: taskId={}, endTime={}", taskId, endTime);

        // 执行更新操作
        jobMapper.updateById(updateObj);
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

    private Long validateRequiredField(JsonNode jsonNode, String fieldName) {
        if (!jsonNode.has(fieldName)) {
            throw new IllegalArgumentException("handlerParam 缺少 " + fieldName + " 字段");
        }
        return jsonNode.get(fieldName).asLong();
    }

    private List<Long> extractFieldAsLongList(JsonNode jsonNode, String fieldName) {
        List<Long> resultList = new ArrayList<>();
        if (jsonNode.has(fieldName) && jsonNode.get(fieldName).isArray()) {
            for (JsonNode node : jsonNode.get(fieldName)) {
                resultList.add(node.asLong());
            }
        }
        return resultList;
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

                log.debug("【Script任务】taskId: {} 模型: {} 脚本执行完成，题目ID: {}, answer: {}", taskId, modelInfo.getModelName()+":"+modelInfo.getModelVersion(), question.getQuestionId(), answerStr);
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
                log.debug("taskId: {} 模型: {} 题目: {} 的答案: {}", taskId, modelInfo.getModelName()+":"+modelInfo.getModelVersion(), question.getQuestionId(), modelAnswer);

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


    private String parseModelAnswer(String modelAnswer) throws JsonProcessingException {
        JsonNode rootNode = new ObjectMapper().readTree(modelAnswer);
        JsonNode choicesNode = rootNode.path("choices");
        if (choicesNode.isArray() && !choicesNode.isEmpty()) {
            JsonNode messageNode = choicesNode.get(0).path("message");
            return messageNode.path("content").asText("");
        }
        return modelAnswer;
    }

    private ModelInfoDO validateModelInfoExists(Long id) {
        ModelInfoDO modelInfo = modelInfoMapper.selectById(id);
        if (modelInfo == null) {
            throw exception(MODEL_NOT_EXISTS_ERROR);
        }

        try {
            Boolean useScript = modelInfo.getUseScript();
            if (!useScript) {
                modelInfo.setApikeys(AESUtil.decrypt(modelInfo.getApikeys())); // API Key 解密
            }
        } catch (Exception e) {
            throw exception(MODEL_APIKEY_ENCRYPT_ERROR);
        }

        return modelInfo;
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
}
