package com.chinatelecom.aigc.evaluate.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.ObjectUtil;
import com.alibaba.fastjson.JSON;
import com.chinatelecom.aigc.evaluate.common.exception.ServiceException;
import com.chinatelecom.aigc.evaluate.common.pojo.PageResult;
import com.chinatelecom.aigc.evaluate.designpattern.strategy.AbstractStrategyChoose;
import com.chinatelecom.aigc.evaluate.domain.QuestionSetInfoDO;
import com.chinatelecom.aigc.evaluate.domain.QuestionSetItemDO;
import com.chinatelecom.aigc.evaluate.dto.req.QuestionSetPageReq;
import com.chinatelecom.aigc.evaluate.dto.req.QuestionSetSaveReq;
import com.chinatelecom.aigc.evaluate.dto.req.QuestionSetUpdateReq;
import com.chinatelecom.aigc.evaluate.dto.resp.QuestionSetItemResp;
import com.chinatelecom.aigc.evaluate.framework.mybatis.core.query.LambdaQueryWrapperX;
import com.chinatelecom.aigc.evaluate.mapper.QuestionMapper;
import com.chinatelecom.aigc.evaluate.mapper.QuestionSetInfoMapper;
import com.chinatelecom.aigc.evaluate.mapper.QuestionSetItemMapper;
import com.chinatelecom.aigc.evaluate.service.QuestionSetItemService;
import com.chinatelecom.aigc.evaluate.service.QuestionSetService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.chinatelecom.aigc.evaluate.common.exception.enums.ErrorCodeConstants.*;
import static com.chinatelecom.aigc.evaluate.common.exception.util.ServiceExceptionUtil.exception;

@Slf4j
@Service
public class QuestionSetServiceImpl implements QuestionSetService {

    private final QuestionSetInfoMapper questionSetInfoMapper;
    private final QuestionSetItemMapper questionSetItemMapper;
    private final AbstractStrategyChoose abstractStrategyChoose;
    private final QuestionMapper questionMapper;
    private final QuestionSetItemService questionSetItemService;

    public QuestionSetServiceImpl(QuestionSetInfoMapper questionSetInfoMapper,
                                  QuestionSetItemMapper questionSetItemMapper,
                                  AbstractStrategyChoose abstractStrategyChoose,
                                  QuestionMapper questionMapper,
                                  QuestionSetItemService questionSetItemService) {
        this.questionSetInfoMapper = questionSetInfoMapper;
        this.questionSetItemMapper = questionSetItemMapper;
        this.abstractStrategyChoose = abstractStrategyChoose;
        this.questionMapper = questionMapper;
        this.questionSetItemService = questionSetItemService;
    }

    @Override
    @Transactional
    public QuestionSetInfoDO create(QuestionSetSaveReq req) {
        // 验证入参
        checkQuestionSave(req);
        QuestionSetInfoDO questionSetInfoDO = QuestionSetInfoDO.builder()
                .questionSetName(req.getQuestionSetName())
                .evaluationTarget("0") // 暂时默认赋值为0
                .description(req.getDescription())
                .questionCategory(String.join(",", req.getQuestionCategory()))
                .extractConf(JSON.toJSONString(req.getExtractConf()))
                .build();
        questionSetInfoMapper.insert(questionSetInfoDO);

        // 立即执行生成题集
        execute(questionSetInfoDO.getId());
        return questionSetInfoDO;
    }

    @Override
    @Transactional(rollbackFor = ServiceException.class)
    public void delete(Long id) {
        questionSetInfoMapper.deleteById(id);
        // 删除对应题集映射
        questionSetItemMapper.delete(new LambdaQueryWrapperX<QuestionSetItemDO>()
                .eq(QuestionSetItemDO::getQuestionSetId, id));
    }

    @Override
    public QuestionSetInfoDO get(Long id) {
        QuestionSetInfoDO questionSetInfoDO = questionSetInfoMapper.selectById(id);
        if (ObjectUtil.isNull(questionSetInfoDO)) {
            throw exception(QUEUSTION_SET_NOT_EXISTS_ERROR);
        }
        return questionSetInfoDO;
    }

    @Override
    public QuestionSetInfoDO get(Long id, Boolean contain) {
        QuestionSetInfoDO questionSetInfoDO = get(id);
        if(contain){
            Map<String, List<QuestionSetItemResp>> itemMap = questionSetItemService.list(Collections.singletonList(questionSetInfoDO.getId()), true)
                    .stream().collect(Collectors.groupingBy(QuestionSetItemResp::getCategory));
            questionSetInfoDO.setQuestionSetItemMap(itemMap);
        }
        return questionSetInfoDO;
    }

    private void checkQuestionSave(QuestionSetSaveReq req) {
        //验证唯一值
        QuestionSetInfoDO questionSetInfoDO = getByQuestionSetName(req.getQuestionSetName());
        if (ObjectUtil.isNotNull(questionSetInfoDO)) {
            throw exception(QUEUSTION_SET_NAME_EXISTS_ERROR);
        }
    }


    private void checkQuestionUpdate(QuestionSetUpdateReq req) {
        if (StringUtils.isBlank(req.getQuestionSetName())) {
            throw exception(QUEUSTION_SET_NAME_NULL_ERROR);
        }
        Long rowCount = questionSetInfoMapper.selectCount(new LambdaQueryWrapperX<QuestionSetInfoDO>()
                .eq(QuestionSetInfoDO::getQuestionSetName, req.getQuestionSetName())
                .ne(QuestionSetInfoDO::getId, req.getId()));
        if (rowCount >= 1) {
            throw exception(QUEUSTION_SET_NAME_EXISTS_ERROR);
        }
    }

    @Override
    public PageResult<QuestionSetInfoDO> getQuestionSetPage(QuestionSetPageReq req) {
        return questionSetInfoMapper.selectPage(req);
    }

    @Override
    public void update(QuestionSetUpdateReq req) {
        QuestionSetInfoDO questionSetInfoDO = questionSetInfoMapper.selectById(req.getId());
        if (ObjectUtil.isNull(questionSetInfoDO)) {
            throw exception(QUEUSTION_SET_NOT_EXISTS_ERROR);
        }
        // 验证修改参数
        checkQuestionUpdate(req);
        questionSetInfoDO.setQuestionSetName(req.getQuestionSetName());
        questionSetInfoDO.setEvaluationTarget("0"); // 暂时赋值为0
        questionSetInfoDO.setDescription(req.getDescription());
        questionSetInfoDO.setQuestionCategory(String.join(",", req.getQuestionCategory()));

        // 判断是否需要更新题目信息
        List<String> forwardQuestionIdList = req.getExtractConf().getForwardConf().getCustomConf().getQuestionIdList();
        List<String> negativeQuestionIdList = req.getExtractConf().getNegativeConf().getCustomConf().getQuestionIdList();
        List<QuestionSetItemDO> questionSetItemDOList = new ArrayList<>();
        if (CollectionUtil.isNotEmpty(forwardQuestionIdList)) {
            questionMapper.selectList(forwardQuestionIdList).forEach(questionDO -> {
                QuestionSetItemDO questionSetItemDO = new QuestionSetItemDO();
                questionSetItemDO.setQuestionId(questionDO.getQuestionId());
                questionSetItemDO.setQuestionVersion(questionDO.getVersion());
                questionSetItemDO.setQuestionSetId(questionSetInfoDO.getId());
                questionSetItemDOList.add(questionSetItemDO);
            });
        }
        if (CollectionUtil.isNotEmpty(negativeQuestionIdList)) {
            questionMapper.selectList(negativeQuestionIdList).forEach(questionDO -> {
                QuestionSetItemDO questionSetItemDO = new QuestionSetItemDO();
                questionSetItemDO.setQuestionId(questionDO.getQuestionId());
                questionSetItemDO.setQuestionVersion(questionDO.getVersion());
                questionSetItemDO.setQuestionSetId(questionSetInfoDO.getId());
                questionSetItemDOList.add(questionSetItemDO);
            });
        }

        // 对新题目操作
        if (CollectionUtil.isNotEmpty(questionSetItemDOList)) {
            // 先删除
            questionSetItemMapper.delete(new LambdaQueryWrapperX<QuestionSetItemDO>()
                    .eq(QuestionSetItemDO::getQuestionSetId, questionSetInfoDO.getId()));
            // 后添加
            questionSetItemMapper.insertBatch(questionSetItemDOList);
        }

        questionSetInfoMapper.updateById(questionSetInfoDO);
    }

    public QuestionSetInfoDO getByQuestionSetName(String questionSetName) {
        return questionSetInfoMapper
                .selectOne(new LambdaQueryWrapperX<QuestionSetInfoDO>()
                        .eq(QuestionSetInfoDO::getQuestionSetName, questionSetName));
    }

    @Override
    @Transactional(rollbackFor = ServiceException.class)
    public void execute(Long id) {
        //1 根据id获取题集信息，判断是否已经生成题集
        QuestionSetInfoDO questionSetInfoDO = questionSetInfoMapper.selectById(id);
        if (ObjectUtil.isNull(questionSetInfoDO)) {
            throw exception(QUEUSTION_SET_NOT_EXISTS_ERROR);
        }
        if (questionSetInfoDO.getGenerate()) {
            throw exception(QUEUSTION_SET_GENERATE_ERROR);
        }
        // 2. 执行指定策略
        for (String category : questionSetInfoDO.getQuestionCategory().split(",")) {
            abstractStrategyChoose.chooseAndExecuteResp(category, questionSetInfoDO);
        }
        // 3.修改生成状态
        questionSetInfoDO.setGenerate(true);
        questionSetInfoMapper.updateById(questionSetInfoDO);
    }
}
