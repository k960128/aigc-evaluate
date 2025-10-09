package com.chinatelecom.aigc.evaluate.listener;

import cn.hutool.core.collection.CollectionUtil;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.chinatelecom.aigc.evaluate.common.enums.BusinessTypeEnum;
import com.chinatelecom.aigc.evaluate.common.enums.QuestionCategoryEnum;
import com.chinatelecom.aigc.evaluate.common.enums.QuestionDifficultyEnum;
import com.chinatelecom.aigc.evaluate.common.exception.ErrorCode;
import com.chinatelecom.aigc.evaluate.domain.QuestionDO;
import com.chinatelecom.aigc.evaluate.domain.QuestionInfoLogDO;
import com.chinatelecom.aigc.evaluate.domain.QuestionSnapshotDO;
import com.chinatelecom.aigc.evaluate.dto.req.QuestionImportReq;
import com.chinatelecom.aigc.evaluate.dto.resp.QuestionTagResp;
import com.chinatelecom.aigc.evaluate.mapper.QuestionInfoLogMapper;
import com.chinatelecom.aigc.evaluate.mapper.QuestionMapper;
import com.chinatelecom.aigc.evaluate.mapper.QuestionSnapshotMapper;
import com.chinatelecom.aigc.evaluate.service.QuestionTagMappingService;
import com.chinatelecom.aigc.evaluate.service.QuestionTagService;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static com.chinatelecom.aigc.evaluate.common.exception.util.ServiceExceptionUtil.exception;

/**
 * excel导入题目监听器
 *
 * @author AIGC
 */
@Slf4j
public class ConsumerImportQuestionListener extends AnalysisEventListener<QuestionImportReq> {
    private final QuestionMapper questionMapper;
    private final QuestionTagService questionTagService;
    private final QuestionTagMappingService questionTagMappingService;
    private final QuestionInfoLogMapper questionInfoLogMapper;
    private final QuestionSnapshotMapper questionSnapshotMapper;
    private static final Integer BATCH_COUNT = 10000;
    private static List<QuestionImportReq> addQuestionImportList = new ArrayList<>(BATCH_COUNT);
    private static List<QuestionImportReq> updateQuestionImportList = new ArrayList<>(BATCH_COUNT);
    private Map<String, QuestionDO> questionMap;
    private Map<String, List<QuestionTagResp>> tagTreeMap;
    private List<String> questionHashList = new ArrayList<>();
    private Long batchNo;
    @Getter
    private AtomicInteger notChangeCount = new AtomicInteger(0);
    @Getter
    private AtomicInteger importedCount = new AtomicInteger(0);
    @Getter
    private AtomicInteger addCount = new AtomicInteger(0);
    @Getter
    private AtomicInteger updateCount = new AtomicInteger(0);
    @Getter
    private Map<String, String> failedRows = new ConcurrentHashMap<>();

    public ConsumerImportQuestionListener(
            Long batchNo,
            QuestionMapper questionMapper,
            QuestionTagService questionTagService,
            QuestionTagMappingService questionTagMappingService,
            QuestionInfoLogMapper questionInfoLogMapper,
            QuestionSnapshotMapper questionSnapshotMapper) {
        this.questionMapper = questionMapper;
        this.questionTagService = questionTagService;
        this.questionTagMappingService = questionTagMappingService;
        this.questionInfoLogMapper = questionInfoLogMapper;
        this.questionSnapshotMapper = questionSnapshotMapper;
        this.batchNo = batchNo;
        getQuestionMap();
        getTagTreeMap();
    }

    private Map<String, QuestionDO> getQuestionMap() {
        if (CollectionUtil.isNotEmpty(questionMap)) {
            return questionMap;
        }
        questionMap = questionMapper.selectList().stream()
                .collect(Collectors.toMap(QuestionDO::getQuestionId, questionDO -> questionDO));
        return questionMap;
    }

    private List<String> getQuestionHashList() {
        if (CollectionUtil.isNotEmpty(questionHashList)) {
            return questionHashList;
        }
        if (CollectionUtil.isNotEmpty(questionMap)) {
            questionHashList = questionMap.values()
                    .stream().map(QuestionDO::getContentHash).collect(Collectors.toList());
        }
        return questionHashList;
    }

    private Map<String, List<QuestionTagResp>> getTagTreeMap() {
        if (CollectionUtil.isNotEmpty(tagTreeMap)) {
            return tagTreeMap;
        }
        tagTreeMap = questionTagService.listTree().stream()
                .collect(Collectors.groupingBy(QuestionTagResp::getTagName));
        return tagTreeMap;
    }

    /**
     * 读取每一行数据
     *
     */
    @Override
    public void invoke(QuestionImportReq data, AnalysisContext context) {
        generateImportReq(data, context);
        if (addQuestionImportList.size() >= BATCH_COUNT) {
            saveBatchImport();
            addQuestionImportList = new ArrayList<>(BATCH_COUNT);
        }
        if (updateQuestionImportList.size() >= BATCH_COUNT) {
            updateBatchImport();
            updateQuestionImportList = new ArrayList<>(BATCH_COUNT);
        }
    }

    /**
     * 所有读取执行完毕时调用
     *
     */
    @Override
    public void doAfterAllAnalysed(AnalysisContext context) {
        saveBatchImport();
        updateBatchImport();
        addQuestionImportList.clear();
        updateQuestionImportList.clear();
    }


    private void generateImportReq(QuestionImportReq data, AnalysisContext context) {
        data.setRowNum(context.readRowHolder().getRowIndex() + 1);
        //默认先设置标签为NULL
        String firstTagId = "";
        String secondTagId = "";
        if (StringUtils.isNotBlank(data.getFirstTag())) {
            List<QuestionTagResp> questionTagResps = getTagTreeMap().get(data.getFirstTag());
            if (CollectionUtil.isNotEmpty(questionTagResps)) {
                firstTagId = questionTagResps.get(0).getTagId();
                List<QuestionTagResp> secondTagInfos = questionTagResps.get(0).getChild().stream().filter(f -> StringUtils.equals(f.getTagName(), data.getSecondTag())).collect(Collectors.toList());
                if (CollectionUtil.isNotEmpty(secondTagInfos)) {
                    secondTagId = secondTagInfos.get(0).getTagId();
                }
            }
        }
        // 设置标签空值
        data.setTags("");
        if (StringUtils.isNotBlank(firstTagId)) {
            data.setTags(firstTagId);
            if (StringUtils.isNotBlank(secondTagId)) {
                data.setTags(String.format("%s,%s", firstTagId, secondTagId));
            }
        }

        // 设置枚举值
        data.setCategory(QuestionCategoryEnum.getCodeByDesc(data.getCategory()));
        data.setDifficulty(QuestionDifficultyEnum.getCodeByDesc(data.getDifficulty()));
        // 判断是否需要修改的数据
        if (getQuestionMap().containsKey(data.getQuestionId())) {
            QuestionDO questionDO = getQuestionMap().get(data.getQuestionId());
            data.setIsUpdate(true);
            String contentHash = questionDO.getContentHash();
            String generateMd5Hash = QuestionDO.generateMd5Hash(data.getTitle(), data.getCategory(), data.getTags(), data.getDifficulty(), data.getAttackMethod(), data.getDataSource());
            data.setChanged(!StringUtils.equals(contentHash, generateMd5Hash));
            if (StringUtils.equals(contentHash, generateMd5Hash)) {
                notChangeCount.getAndIncrement();
            } else {
                updateQuestionImportList.add(data);
            }
        } else {
            // 验证题目是否存在
            String generateMd5Hash = QuestionDO.generateMd5Hash(data.getTitle(), data.getCategory(), data.getTags(), data.getDifficulty(), data.getAttackMethod(), data.getDataSource());
            if (getQuestionHashList().contains(generateMd5Hash)) {
                failedRows.put(String.valueOf(data.getRowNum()), String.format("{%s} 行重复", data.getRowNum()));
            } else {
                data.setIsCreate(true);
                addQuestionImportList.add(data);
                getQuestionHashList().add(generateMd5Hash);
            }
        }
        importedCount.getAndIncrement();
    }


    /**
     * 导入新增
     *
     */
    public void saveBatchImport() {
        List<QuestionImportReq> importReqList = addQuestionImportList;
        List<QuestionDO> questionDOS = new ArrayList<>();
        List<QuestionInfoLogDO> infoLogDOS = new ArrayList<>();
        importReqList.forEach(each -> {
            QuestionDO questionDO = QuestionDO.createImportDO(each);
            try {
                // 先判断枚举值
                if (questionDO.getCategory().equals("NULL")) {
                    throw exception(new ErrorCode(9999999, "枚举异常"));
                }
                if (questionDO.getDifficulty().equals("NULL")) {
                    throw exception(new ErrorCode(9999999, "枚举异常"));
                }
                questionDOS.add(questionDO);
                infoLogDOS.add(QuestionInfoLogDO.builder()
                        .questionId(questionDO.getQuestionId())
                        .operationType(BusinessTypeEnum.INSERT.name())
                        .operationMode(BusinessTypeEnum.IMPORT.name())
                        .sourceVersion(questionDO.getVersion())
                        .targetVersion(questionDO.getVersion())
                        .batchNo(batchNo)
                        .build());
                addCount.getAndIncrement();
            } catch (Exception e) {
                log.error(e.getMessage());
                failedRows.put(each.getRowNum() + "", e.getMessage());
            }
        });
        if (CollectionUtil.isNotEmpty(questionDOS)) {
            // 批量导入题目
            log.info("开始批量导入题目,size:{}", questionDOS.size());
            questionMapper.insertBatch(questionDOS);
            // 批量导入标签
            log.info("开始批量导入标签,size:{}", questionDOS.size());
            questionTagMappingService.batchCreateMapping(questionDOS);
        }
        if (CollectionUtil.isNotEmpty(infoLogDOS)) {
            // 批量导入日志
            log.info("开始批量导入日志,size:{}", infoLogDOS.size());
            questionInfoLogMapper.insertBatch(infoLogDOS);
        }
    }


    /**
     * 批量导入修改
     *
     */
    public void updateBatchImport() {
        List<QuestionImportReq> importReqList = updateQuestionImportList;

        List<QuestionDO> questionDOS = new ArrayList<>();
        List<QuestionInfoLogDO> infoLogDOS = new ArrayList<>();
        List<QuestionSnapshotDO> snapshotDOS = new ArrayList<>();
        importReqList.forEach(each -> {
            QuestionDO questionDO = questionMap.get(each.getQuestionId());
            QuestionDO updateDO = QuestionDO.updateImportDO(each, questionDO.getId(), questionDO.getVersion());
            try {
                // 先判断枚举值
                if (updateDO.getCategory().equals("NULL")) {
                    throw exception(new ErrorCode(9999999, "枚举异常"));
                }
                if (updateDO.getDifficulty().equals("NULL")) {
                    throw exception(new ErrorCode(9999999, "枚举异常"));
                }
                questionDOS.add(updateDO);
                snapshotDOS.add(QuestionSnapshotDO.create(questionDO));
                infoLogDOS.add(QuestionInfoLogDO.builder()
                        .questionId(questionDO.getQuestionId())
                        .operationType(BusinessTypeEnum.UPDATE.name())
                        .operationMode(BusinessTypeEnum.IMPORT.name())
                        .sourceVersion(updateDO.getVersion())
                        .targetVersion(updateDO.getVersion() + 1)
                        .batchNo(batchNo)
                        .build());
                updateCount.getAndIncrement();
            } catch (RuntimeException e) {
                failedRows.put(each.getRowNum() + "", e.getMessage());
            }
        });

        if (CollectionUtil.isNotEmpty(questionDOS)) {
            // 批量修改导入题目
            log.info("批量修改导入题目,size:{}", questionDOS.size());
            questionMapper.updateBatch(questionDOS);
            // 批量修改对标签操作
            log.info("批量修改对标签操作,size:{}", questionDOS.size());
            questionTagMappingService.batchCreateMapping(questionDOS);
        }
        if (CollectionUtil.isNotEmpty(snapshotDOS)) {
            // 批量快照操作
            log.info("开始批量修改快照操作,size:{}", snapshotDOS.size());
            questionSnapshotMapper.insertBatch(snapshotDOS);
        }
        if (CollectionUtil.isNotEmpty(infoLogDOS)) {
            // 日志批量操作
            log.info("开始日志批量修改操作,size:{}", infoLogDOS.size());
            questionInfoLogMapper.insertBatch(infoLogDOS);
        }
    }
}
