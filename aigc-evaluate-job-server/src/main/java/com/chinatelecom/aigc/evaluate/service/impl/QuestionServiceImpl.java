package com.chinatelecom.aigc.evaluate.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.collection.ListUtil;
import cn.hutool.core.util.ObjectUtil;
import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.write.metadata.WriteSheet;
import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chinatelecom.aigc.evaluate.common.enums.BusinessTypeEnum;
import com.chinatelecom.aigc.evaluate.common.enums.ImportCommonEnum;
import com.chinatelecom.aigc.evaluate.common.enums.QuestionCategoryEnum;
import com.chinatelecom.aigc.evaluate.common.enums.QuestionDifficultyEnum;
import com.chinatelecom.aigc.evaluate.common.exception.ServiceException;
import com.chinatelecom.aigc.evaluate.common.pojo.PageResult;
import com.chinatelecom.aigc.evaluate.common.util.snow.CodeUtils;
import com.chinatelecom.aigc.evaluate.domain.*;
import com.chinatelecom.aigc.evaluate.dto.req.*;
import com.chinatelecom.aigc.evaluate.dto.resp.QuestionBatchResp;
import com.chinatelecom.aigc.evaluate.dto.resp.QuestionExportResp;
import com.chinatelecom.aigc.evaluate.dto.resp.QuestionImportResp;
import com.chinatelecom.aigc.evaluate.excel.core.constant.ExcelConstants;
import com.chinatelecom.aigc.evaluate.framework.mybatis.core.query.LambdaQueryWrapperX;
import com.chinatelecom.aigc.evaluate.listener.ConsumerImportQuestionListener;
import com.chinatelecom.aigc.evaluate.mapper.*;
import com.chinatelecom.aigc.evaluate.service.ImportQuestionLogService;
import com.chinatelecom.aigc.evaluate.service.QuestionService;
import com.chinatelecom.aigc.evaluate.service.QuestionTagMappingService;
import com.chinatelecom.aigc.evaluate.service.QuestionTagService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import static com.chinatelecom.aigc.evaluate.common.exception.enums.ErrorCodeConstants.*;
import static com.chinatelecom.aigc.evaluate.common.exception.util.ServiceExceptionUtil.exception;

@Slf4j
@Service
public class QuestionServiceImpl implements QuestionService {

    private final QuestionMapper questionMapper;
    private final QuestionSnapshotMapper questionSnapshotMapper;
    private final QuestionInfoLogMapper questionInfoLogMapper;
    private final QuestionSetItemMapper questionSetItemMapper;
    private final QuestionTagService questionTagService;
    private final QuestionTagMappingService questionTagMappingService;
    private final ImportQuestionLogService importQuestionLogService;
    private final ReentrantLock lock;
    private final QuestionTagInfoMapper questionTagInfoMapper;
    private final QuestionTagMappingMapper questionTagMappingMapper;

    public QuestionServiceImpl(QuestionMapper questionMapper,
                               QuestionSnapshotMapper questionSnapshotMapper,
                               QuestionInfoLogMapper questionInfoLogMapper,
                               QuestionSetItemMapper questionSetItemMapper,
                               QuestionTagService questionTagService,
                               QuestionTagMappingService questionTagMappingService,
                               ImportQuestionLogService importQuestionLogService, QuestionTagInfoMapper questionTagInfoMapper, QuestionTagMappingMapper questionTagMappingMapper) {
        this.questionMapper = questionMapper;
        this.questionSnapshotMapper = questionSnapshotMapper;
        this.questionInfoLogMapper = questionInfoLogMapper;
        this.questionSetItemMapper = questionSetItemMapper;
        this.questionTagService = questionTagService;
        this.questionTagMappingService = questionTagMappingService;
        this.importQuestionLogService = importQuestionLogService;
        this.lock = new ReentrantLock();
        this.questionTagInfoMapper = questionTagInfoMapper;
        this.questionTagMappingMapper = questionTagMappingMapper;
    }

    @Override
    @Transactional(rollbackFor = ServiceException.class)
    public QuestionDO create(QuestionSaveReq req) {
        QuestionDO questionDO = QuestionDO.create(req);
//        if (checkQuestionContentExist(questionDO.getContentHash())) {
//            throw exception(QUESTION_SAVE_ERROR, "内容重复");
//        }
        questionMapper.insert(questionDO);
        // 创建标签关联映射关系
        questionTagMappingService.createMapping(questionDO.getQuestionId(), req.getTags());
        // 记录日志
        questionInfoLogMapper.insert(QuestionInfoLogDO.builder()
                .questionId(questionDO.getQuestionId())
                .operationType(BusinessTypeEnum.INSERT.name())
                .operationMode(BusinessTypeEnum.SINGLE.name())
                .sourceVersion(questionDO.getVersion())
                .targetVersion(questionDO.getVersion())
                .batchNo(CodeUtils.getSnowFlakeId())
                .build());
        return questionDO;
    }

    @Override
    @Transactional(rollbackFor = ServiceException.class)
    public int update(QuestionUpdateReq req) {
        QuestionDO questionDO = questionMapper.selectById(req.getId());
        if (questionDO == null) {
            throw exception(QUESTION_NOT_EXISTS_ERROR);
        }
        // 验证MD5值,不相同执行修改
        if (!questionDO.getContentHash().equals(QuestionDO.generateMd5Hash(req.getTitle(), req.getCategory(), req.getTags(), req.getDifficulty(), req.getAttackMethod(), req.getDataSource(), req.getDesc()))) {
            QuestionDO updateDO = QuestionDO.update(req, questionDO.getVersion());
            // 先修改数据
            int row = questionMapper.updateByQuestionId(updateDO);
            // 判断是否修改成功
            if (row == 0) {
                throw exception(QUESTION_UPDATE_ERROR, "修改内容已发生变动，请重新操作");
            }
            // 重新生成标签关联映射关系
            questionTagMappingService.createMapping(questionDO.getQuestionId(), req.getTags());
            // 后生成旧数据快照
            questionSnapshotMapper.insert(QuestionSnapshotDO.create(questionDO));
            // 记录日志
            questionInfoLogMapper.insert(QuestionInfoLogDO.builder()
                    .questionId(questionDO.getQuestionId())
                    .operationType(BusinessTypeEnum.UPDATE.name())
                    .operationMode(BusinessTypeEnum.SINGLE.name())
                    .sourceVersion(updateDO.getVersion())
                    .targetVersion(updateDO.getVersion() + 1)
                    .batchNo(CodeUtils.getSnowFlakeId())
                    .build());
            return 1;
        }
        return 0;
    }

    /**
     * 校验题目是否存在
     *
     * @param questionId 题目唯一ID
     * @return boolean
     */
    private Boolean checkQuestionExist(String questionId) {
        QuestionDO questionDO = questionMapper.selectOne(new LambdaQueryWrapper<QuestionDO>()
                .eq(QuestionDO::getQuestionId, questionId));
        return questionDO != null;
    }

    private Boolean checkQuestionContentExist(String token){
        QuestionDO questionDO = questionMapper.selectOne(new LambdaQueryWrapper<QuestionDO>()
                .eq(QuestionDO::getContentHash, token));
        return questionDO != null;
    }

    @Override
    public QuestionDO getByQuestion(String questionId) {
        return questionMapper.selectOne(new LambdaQueryWrapper<QuestionDO>()
                .eq(QuestionDO::getQuestionId, questionId));
    }

    @Override
    public int delete(String questionId) {
        QuestionDO questionDO = getByQuestion(questionId);
        if (ObjectUtil.isNull(questionDO)) {
            throw exception(QUESTION_DELETE_ERROR, "该题目不存在");
        }
        // 先新增变动快照
        questionSnapshotMapper.insert(QuestionSnapshotDO.create(questionDO));
        // 删除对应标签信息
        questionTagMappingService.deleteMapping(questionDO.getQuestionId());
        // 删除题目集映射对应题目
        questionSetItemMapper.delete(new LambdaQueryWrapper<QuestionSetItemDO>()
                .eq(QuestionSetItemDO::getQuestionId, questionDO.getQuestionId())
                .eq(QuestionSetItemDO::getQuestionVersion, questionDO.getVersion()));
        // 删除数据
        int row = questionMapper.deleteByQuestionId(questionDO);
        // 记录日志
        questionInfoLogMapper.insert(QuestionInfoLogDO.builder()
                .questionId(questionDO.getQuestionId())
                .operationType(BusinessTypeEnum.DELETE.name())
                .operationMode(BusinessTypeEnum.SINGLE.name())
                .sourceVersion(questionDO.getVersion())
                .targetVersion(questionDO.getVersion())
                .batchNo(CodeUtils.getSnowFlakeId())
                .build());
        return row;
    }

    @Override
    public QuestionBatchResp batchDelete(List<String> questionIds) {
        QuestionBatchResp questionBatchResp = new QuestionBatchResp();
        if (CollectionUtil.isEmpty(questionIds)) {
            throw exception(QUESTION_BATCH_DELETE_ERROR, "题目ID集合不能为空");
        }
        // 获取集合信息
        List<QuestionDO> questionDOS = questionMapper.selectList(questionIds);
        if (CollectionUtil.isNotEmpty(questionDOS)) {
            List<QuestionSnapshotDO> questionSnapshotDOList = questionDOS.stream().map(QuestionSnapshotDO::create).collect(Collectors.toList());
            // 先批量记录快照
            questionSnapshotMapper.insertBatch(questionSnapshotDOList);
            // 再删除题目数据
            int successCount = questionMapper.deleteBatchByQuestionId(questionIds);
            questionBatchResp.setDeleted_count(String.format("%s", successCount));
            questionBatchResp.setFailed_count(String.format("%s", questionIds.size() - successCount));
            Long batchNo = CodeUtils.getSnowFlakeId();
            questionSnapshotDOList.forEach(each -> {
                // 删除题目集映射对应题目
                questionSetItemMapper.delete(new LambdaQueryWrapper<QuestionSetItemDO>()
                        .eq(QuestionSetItemDO::getQuestionId, each.getQuestionId())
                        .eq(QuestionSetItemDO::getQuestionVersion, each.getVersion()));
                // 删除对应标签信息
                questionTagMappingService.deleteMapping(each.getQuestionId());
                // 记录日志
                questionInfoLogMapper.insert(QuestionInfoLogDO.builder()
                        .questionId(each.getQuestionId())
                        .operationType(BusinessTypeEnum.DELETE.name())
                        .operationMode(BusinessTypeEnum.BATCH.name())
                        .sourceVersion(each.getVersion())
                        .targetVersion(each.getVersion())
                        .batchNo(batchNo)
                        .build());
            });
        }
        return questionBatchResp;
    }

    @Override
    public PageResult<QuestionDO> getQuestionPage(QuestionPageReq questionPageReq) {
        if (StringUtils.isNotBlank(questionPageReq.getTags())) {
            ArrayList<String> tagIds = ListUtil.toList(questionPageReq.getTags().split(","));
            List<QuestionTagMappingDO> questionTagList = questionTagMappingService.listByTagIds(tagIds);
            Set<String> questionIds = questionTagList.stream().map(QuestionTagMappingDO::getQuestionId).collect(Collectors.toSet());
            if (CollectionUtil.isNotEmpty(questionIds)) {
                return questionMapper.selectPageContainTag(questionPageReq, questionIds);
            }
            return new PageResult<>(new ArrayList<>(), 0L);
        }
        return questionMapper.selectPage(questionPageReq);
    }

    @Override
    public QuestionImportResp importQuestionList(MultipartFile file) {
        if (lock.tryLock()) {
            // 初始化返回结果
            QuestionImportResp questionImportResp = new QuestionImportResp();
            ImportQuestionLogDO importQuestionLogDO = new ImportQuestionLogDO();
            Map<String, String> contentMap;
            try {
                // 创建导入日志
                log.info("创建导入任务");
                importQuestionLogDO = importQuestionLogService.create(ImportQuestionLogDO.builder()
                        .batchNo(CodeUtils.getSnowFlakeId())
                        .runState(ImportCommonEnum.START.name())
                        .fileName(file.getOriginalFilename())
                        .build());
                log.info("importQuestionLogDO:{}", importQuestionLogDO);
                // 创建自定义数据读取监听器
                ConsumerImportQuestionListener listener =
                        new ConsumerImportQuestionListener(
                                importQuestionLogDO.getBatchNo(),
                                questionMapper,
                                questionTagService,
                                questionTagMappingService,
                                questionInfoLogMapper,
                                questionSnapshotMapper);
                // Excel数据读取
                EasyExcel.read(file.getInputStream(), QuestionImportReq.class, listener).sheet(0).headRowNumber(2).doRead();

                // 组装返回参数
                questionImportResp.setBatchNo(importQuestionLogDO.getBatchNo());
                questionImportResp.setImported_count(String.valueOf(listener.getImportedCount().get()));
                questionImportResp.setAdd_count(String.valueOf(listener.getAddCount().get()));
                questionImportResp.setUpdate_count(String.valueOf(listener.getUpdateCount().get()));
                questionImportResp.setNot_change_count(String.valueOf(listener.getNotChangeCount().get()));
                questionImportResp.setFailed_count(String.valueOf(listener.getFailedRows().size()));
                questionImportResp.setFailed_rows(listener.getFailedRows());
                importQuestionLogDO.setContent(JSON.toJSONString(questionImportResp));
                importQuestionLogDO.setStatus(ImportCommonEnum.SUCCESS.name());
            } catch (IOException e) {
                log.error(e.getMessage());
                importQuestionLogDO.setStatus(ImportCommonEnum.FAILED.name());
                contentMap = new HashMap<String, String>() {{
                    put("error", e.getMessage());
                }};
                importQuestionLogDO.setContent(JSON.toJSONString(contentMap));
            } catch (RuntimeException runtimeException) {
                log.error(runtimeException.getMessage());
                importQuestionLogDO.setStatus(ImportCommonEnum.FAILED.name());
                contentMap = new HashMap<String, String>() {{
                    put("error", runtimeException.getMessage());
                }};
                importQuestionLogDO.setContent(JSON.toJSONString(contentMap));
            } finally {
                importQuestionLogDO.setUpdateTime(LocalDateTime.now());
                log.info("开始更改导入任务状态");
                importQuestionLogDO.setRunState(ImportCommonEnum.STOP.name());
                log.info("导入任务最终状态:{}", importQuestionLogDO);
                importQuestionLogService.update(importQuestionLogDO);
                lock.unlock();
                log.info("导入任务结束，解锁");
            }
            return questionImportResp;
        } else {
            throw exception(EXCEL_IMPORT_QUESTIONS_ERROR, "已有正在执行中的导入任务");
        }
    }

    @Override
    public List<QuestionDO> getQuestionAll() {
        return questionMapper.selectList();
    }


    @Override
    public void exportExcel(QuestionExportReq questionExportReq, HttpServletResponse response) {
        OutputStream outputStream = null;
        long startTime = System.currentTimeMillis();
        log.info("导出开始时间:{}", startTime);
        String fileName = "习题导出列表-" + System.currentTimeMillis();
        // 获取数据总量
        Integer totalCount = questionMapper.selectCountByQuestionIds(questionExportReq.getQuestionIds());
        // 每一个Sheet存放数据量
        Integer sheetDataRows = ExcelConstants.PER_SHEET_ROW_COUNT;
        // 每次写入的数据量
        Integer writeDataRows = ExcelConstants.PER_WRITE_ROW_COUNT;
        // 计算需要的Sheet数量
        int sheetNum = totalCount % sheetDataRows == 0 ? (totalCount / sheetDataRows) : (totalCount / sheetDataRows + 1);

        // 获取标签信息
        Map<Integer, List<QuestionTagInfoDO>> tagMap = questionTagInfoMapper.selectList().stream().collect(Collectors.groupingBy(QuestionTagInfoDO::getTagLevel));
        try {
            outputStream = response.getOutputStream();
            ExcelWriter excelWriter = EasyExcel.write(outputStream, QuestionExportResp.class).build();
            // 需要写入的sheet页数量
            for (int i = 0; i < sheetNum; i++) {
                // 每次都要创建writeSheet 这里注意必须指定sheetNo 而且sheetName必须不一样
                WriteSheet writeSheet = EasyExcel.writerSheet(i, "习题页码-" + i + 1).build();
                // 分页去数据库查询数据 这里可以去数据库查询每一页的数据
                QuestionPageExportReq page = new QuestionPageExportReq();
                page.setQuestionIds(questionExportReq.getQuestionIds());
                page.setPageNo(i + 1);
                page.setPageSize(writeDataRows);
                List<QuestionDO> questionDOList = questionMapper.selectPage(page).getList();

                // 获取标签映射
                Map<String, List<QuestionTagMappingDO>> tagMappingMap = questionTagMappingMapper.selectList(new LambdaQueryWrapperX<QuestionTagMappingDO>()
                                .in(QuestionTagMappingDO::getQuestionId, questionDOList.stream().map(QuestionDO::getQuestionId).collect(Collectors.toList())))
                        .stream().collect(Collectors.groupingBy(QuestionTagMappingDO::getQuestionId));

                List<QuestionExportResp> data = questionDOList
                        .stream()
                        .map(question -> QuestionExportResp.builder()
                                .questionId(question.getQuestionId())
                                .title(question.getTitle())
                                .category(QuestionCategoryEnum.valueOf(question.getCategory()).getDesc())
                                .firstTag(generateTag(tagMap, tagMappingMap, question.getQuestionId(), 1))
                                .secondTag(generateTag(tagMap, tagMappingMap, question.getQuestionId(), 2))
                                .difficulty(QuestionDifficultyEnum.valueOf(question.getDifficulty()).getDesc())
                                .attackMethod(question.getAttackMethod())
                                .dataSource(question.getDataSource())
                                .build())
                        .collect(Collectors.toList());
                excelWriter.write(data, writeSheet);
                setExcelRespProp(response, fileName);
                excelWriter.finish();
                outputStream.flush();
                long endTime = System.currentTimeMillis();
                log.info("导出结束时间:{}", endTime + "ms");
                log.info("导出所用时间:{}", (endTime - startTime) / 1000 + "秒");
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * 设置excel下载响应头属性
     */
    public static void setExcelRespProp(HttpServletResponse response, String rawFileName) {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        String fileName = null;
        try {
            fileName = URLEncoder.encode(rawFileName, "UTF-8").replaceAll("\\+", "%20");
        } catch (UnsupportedEncodingException e) {
            log.error("设置excel下载响应头属性，失败 {}", e.getMessage());
        }
        response.setHeader("Content-disposition", "attachment;filename=" + fileName + ".xlsx");
    }

    private String generateTag(Map<Integer, List<QuestionTagInfoDO>> tagMap,
                               Map<String, List<QuestionTagMappingDO>> tagMappingMap,
                               String questionId,
                               int level) {
        if (tagMappingMap.containsKey(questionId)) {
            List<QuestionTagInfoDO> tags = tagMap.get(level);
            List<QuestionTagMappingDO> questionTags = tagMappingMap.get(questionId);

            if (tags == null || tags.isEmpty() || questionTags == null || questionTags.isEmpty()) {
                return null; // 或者抛出自定义异常
            }

            Set<String> tagIds = questionTags.stream()
                    .map(QuestionTagMappingDO::getTagId)
                    .collect(Collectors.toSet());

            return tags.stream()
                    .filter(tag -> tagIds.contains(tag.getTagId()))
                    .findFirst()
                    .map(QuestionTagInfoDO::getTagName)
                    .orElse(null); // 如果没有匹配项，返回 null
        }
        return null;
    }
}
