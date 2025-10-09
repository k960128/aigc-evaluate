package com.chinatelecom.aigc.evaluate.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import com.chinatelecom.aigc.evaluate.domain.QuestionDO;
import com.chinatelecom.aigc.evaluate.domain.QuestionTagMappingDO;
import com.chinatelecom.aigc.evaluate.framework.mybatis.core.query.LambdaQueryWrapperX;
import com.chinatelecom.aigc.evaluate.mapper.QuestionTagMappingMapper;
import com.chinatelecom.aigc.evaluate.service.QuestionTagMappingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class QuestionTagMappingServiceImpl implements QuestionTagMappingService {

    private final QuestionTagMappingMapper questionTagMappingMapper;

    public QuestionTagMappingServiceImpl(QuestionTagMappingMapper questionTagMappingMapper) {
        this.questionTagMappingMapper = questionTagMappingMapper;
    }

    @Override
    public void createMapping(String questionId, String tags) {
        List<QuestionTagMappingDO> mappingDOList = Optional.ofNullable(tags)
                .filter(t -> !t.trim().isEmpty())
                .map(t -> Arrays.stream(t.split(","))
                        .filter(tagId -> !tagId.trim().isEmpty())
                        .map(tagId -> QuestionTagMappingDO.create(questionId, tagId))
                        .collect(Collectors.toList()))
                .orElseGet(ArrayList::new);
        if (CollectionUtil.isNotEmpty(mappingDOList)) {
            // 先删除
            deleteMapping(questionId);
            // 后添加
            questionTagMappingMapper.insertBatch(mappingDOList);
        }
    }

    /**
     * 根据题目ID删除关联信息
     * @param questionId 题目ID
     */
    @Override
    public void deleteMapping(String questionId) {
        questionTagMappingMapper.delete(new LambdaQueryWrapperX<QuestionTagMappingDO>()
                .eq(QuestionTagMappingDO::getQuestionId, questionId));
    }

    public void batchDeleteMapping(List<String> questionIds) {
        if (CollectionUtil.isNotEmpty(questionIds)) {
            List<QuestionTagMappingDO> mappingDOList = questionTagMappingMapper.selectList(new LambdaQueryWrapperX<QuestionTagMappingDO>()
                    .in(QuestionTagMappingDO::getQuestionId, questionIds));
            if (CollectionUtil.isNotEmpty(mappingDOList)) {
                questionTagMappingMapper.deleteByIds(mappingDOList);
            }
        }

    }

    @Override
    public void batchCreateMapping(List<QuestionDO> questionDOS) {
        List<QuestionTagMappingDO> questionTagMappingDOList = new ArrayList<>();
        questionDOS.stream()
                .filter(questionDO -> !questionDO.getTags().isEmpty())
                .forEach(questionDO -> {
                    List<QuestionTagMappingDO> mappingDOList = Optional.of(questionDO.getTags())
                            .filter(t -> !t.trim().isEmpty())
                            .map(t -> Arrays.stream(t.split(","))
                                    .filter(tagId -> !tagId.trim().isEmpty())
                                    .map(tagId -> QuestionTagMappingDO.create(questionDO.getQuestionId(), tagId))
                                    .collect(Collectors.toList()))
                            .orElseGet(ArrayList::new);
                    questionTagMappingDOList.addAll(mappingDOList);
                });
        if (CollectionUtil.isNotEmpty(questionTagMappingDOList)) {
            // 先删除
            batchDeleteMapping(questionTagMappingDOList.stream().map(QuestionTagMappingDO::getQuestionId).collect(Collectors.toList()));
            // 后添加
            questionTagMappingMapper.insertBatch(questionTagMappingDOList);
        }
    }

    @Override
    public List<QuestionTagMappingDO> listByTagIds(ArrayList<String> tagIds) {
        if (CollectionUtil.isNotEmpty(tagIds)) {
            return questionTagMappingMapper.selectList(new LambdaQueryWrapperX<QuestionTagMappingDO>()
                    .in(QuestionTagMappingDO::getTagId, tagIds));
        }
        return Collections.emptyList();
    }
}
