package com.chinatelecom.aigc.evaluate.service.impl;

import cn.hutool.core.util.ObjectUtil;
import com.chinatelecom.aigc.evaluate.common.util.object.BeanUtils;
import com.chinatelecom.aigc.evaluate.domain.QuestionTagInfoDO;
import com.chinatelecom.aigc.evaluate.domain.QuestionTagMappingDO;
import com.chinatelecom.aigc.evaluate.dto.req.QuestionTagReq;
import com.chinatelecom.aigc.evaluate.dto.req.QuestionTagSaveReq;
import com.chinatelecom.aigc.evaluate.dto.req.QuestionTagUpdateReq;
import com.chinatelecom.aigc.evaluate.dto.resp.QuestionTagContainQuestionResp;
import com.chinatelecom.aigc.evaluate.dto.resp.QuestionTagResp;
import com.chinatelecom.aigc.evaluate.framework.mybatis.core.query.LambdaQueryWrapperX;
import com.chinatelecom.aigc.evaluate.mapper.QuestionMapper;
import com.chinatelecom.aigc.evaluate.mapper.QuestionTagInfoMapper;
import com.chinatelecom.aigc.evaluate.mapper.QuestionTagMappingMapper;
import com.chinatelecom.aigc.evaluate.service.QuestionTagService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.chinatelecom.aigc.evaluate.common.exception.enums.ErrorCodeConstants.QUESTION_TAG_NAME_EXISTS_ERROR;
import static com.chinatelecom.aigc.evaluate.common.exception.enums.ErrorCodeConstants.QUESTION_TAG_NOT_EXISTS_ERROR;
import static com.chinatelecom.aigc.evaluate.common.exception.util.ServiceExceptionUtil.exception;

@Slf4j
@Service
public class QuestionTagServiceImpl implements QuestionTagService {

    private final QuestionTagInfoMapper questionTagInfoMapper;
    private final QuestionMapper questionMapper;
    private final QuestionTagMappingMapper questionTagMappingMapper;

    public QuestionTagServiceImpl(QuestionTagInfoMapper questionTagInfoMapper,
                                  QuestionMapper questionMapper,
                                  QuestionTagMappingMapper questionTagMappingMapper) {
        this.questionTagInfoMapper = questionTagInfoMapper;
        this.questionMapper = questionMapper;
        this.questionTagMappingMapper = questionTagMappingMapper;
    }

    @Override
    public void create(QuestionTagSaveReq param) {
        // 校验参数
        checkSave(param);
        QuestionTagInfoDO tagInfoDO = QuestionTagInfoDO.create(param, getByTagId(param.getParentId()));
        questionTagInfoMapper.insert(tagInfoDO);
    }

    @Override
    public void update(QuestionTagUpdateReq param) {
        checkUpdate(param);
        QuestionTagInfoDO tagInfoDO = QuestionTagInfoDO.update(param, getByTagId(param.getParentId()));
        questionTagInfoMapper.updateById(tagInfoDO);
    }

    @Override
    public QuestionTagInfoDO getByTagId(String tagId) {
        return questionTagInfoMapper.selectOne(new LambdaQueryWrapperX<QuestionTagInfoDO>()
                .eq(QuestionTagInfoDO::getTagId, tagId));
    }

    @Override
    public QuestionTagResp getByTagIdContainChild(String tagId) {
        QuestionTagResp questionTagResp;

        QuestionTagInfoDO tagInfoDO = getByTagId(tagId);
        if (ObjectUtil.isNull(tagInfoDO)) {
            throw exception(QUESTION_TAG_NOT_EXISTS_ERROR);
        }
        questionTagResp = BeanUtils.toBean(tagInfoDO, QuestionTagResp.class);
        List<QuestionTagInfoDO> childList = questionTagInfoMapper.selectList(new LambdaQueryWrapperX<QuestionTagInfoDO>()
                .eq(QuestionTagInfoDO::getParentId, tagId));
        questionTagResp.setChild(BeanUtils.toBean(childList, QuestionTagResp.class));
        questionTagResp.setCount(childList.size());
        return questionTagResp;
    }

    @Override
    public List<QuestionTagResp> list(QuestionTagReq param) {
        List<QuestionTagInfoDO> questionTagInfoDOList = questionTagInfoMapper.selectList(new LambdaQueryWrapperX<QuestionTagInfoDO>()
                .eq(QuestionTagInfoDO::getCategory, param.getCategory()));

        if (!questionTagInfoDOList.isEmpty()) {
            // 将所有的标签映射根据标签ID进行分组
            Map<String, List<QuestionTagMappingDO>> tagMappingByTagIdMap = questionTagMappingMapper.selectList().stream().collect(Collectors.groupingBy(QuestionTagMappingDO::getTagId));
            Map<String, List<QuestionTagInfoDO>> listMap = questionTagInfoDOList.stream().collect(Collectors.groupingBy(QuestionTagInfoDO::getParentId));
            List<QuestionTagResp> resultList = questionTagInfoDOList.stream().map(tag -> {
                QuestionTagResp questionTagResp = BeanUtils.toBean(tag, QuestionTagResp.class);
                if (tagMappingByTagIdMap.containsKey(questionTagResp.getTagId())) {
                    questionTagResp.setQuestionCount(tagMappingByTagIdMap.get(questionTagResp.getTagId()).size());
                } else {
                    questionTagResp.setQuestionCount(0);
                }
                questionTagResp.setCount(listMap.containsKey(questionTagResp.getTagId()) ? listMap.get(questionTagResp.getTagId()).size() : 0);
                return questionTagResp;
            }).collect(Collectors.toList());
            return resultList;
        }
        return new ArrayList<>();
    }

    @Override
    public List<QuestionTagResp> listTree() {
        List<QuestionTagInfoDO> questionTagInfoDOList = questionTagInfoMapper.selectList();
        return generateTree(questionTagInfoDOList);
    }

    private void checkSave(QuestionTagSaveReq param) {
        Long count = questionTagInfoMapper.selectCount(new LambdaQueryWrapperX<QuestionTagInfoDO>()
                .eq(QuestionTagInfoDO::getTagName, param.getTagName())
                .eq(QuestionTagInfoDO::getParentId, param.getParentId())
        );
        if (count >= 1) {
            throw exception(QUESTION_TAG_NAME_EXISTS_ERROR);
        }
    }

    private void checkUpdate(QuestionTagUpdateReq param) {
        Long count = questionTagInfoMapper.selectCount(new LambdaQueryWrapperX<QuestionTagInfoDO>()
                .eq(QuestionTagInfoDO::getTagName, param.getTagName())
                .eq(QuestionTagInfoDO::getParentId, param.getParentId())
                .ne(QuestionTagInfoDO::getTagId, param.getTagId())
        );
        if (count >= 1) {
            throw exception(QUESTION_TAG_NAME_EXISTS_ERROR);
        }
    }

    /**
     * 生成标签树
     * @param tagInfoDOList t
     * @return tree
     */
    private List<QuestionTagResp> generateTree(List<QuestionTagInfoDO> tagInfoDOList) {

        Map<String, QuestionTagResp> tagMap = new HashMap<>();
        // 将所有的标签映射根据标签ID进行分组
        Map<String, List<QuestionTagMappingDO>> tagMappingByTagIdMap = questionTagMappingMapper.selectList().stream().collect(Collectors.groupingBy(QuestionTagMappingDO::getTagId));
        // 将所有标签转换为 QuestionTagResp 并放入 map 中
        tagInfoDOList.forEach(tag -> tagMap.put(tag.getTagId(), convertToQuestionTagResp(tag)));

        List<QuestionTagResp> rootTags = new ArrayList<>();

        // 构建树结构
        for (QuestionTagInfoDO tag : tagInfoDOList) {
            QuestionTagResp currentTag = tagMap.get(tag.getTagId());
            if (tagMappingByTagIdMap.containsKey(currentTag.getTagId())) {
                currentTag.setQuestionCount(tagMappingByTagIdMap.get(currentTag.getTagId()).size());
            } else {
                currentTag.setQuestionCount(0);
            }
            if (tag.getParentId() == null || !tagMap.containsKey(tag.getParentId())) {
                rootTags.add(currentTag);
            } else {
                QuestionTagResp parentTag = tagMap.get(tag.getParentId());
                parentTag.getChild().add(currentTag);
            }
        }
        // 递归计算每个节点的子节点数量
        rootTags.forEach(each -> calculateChildCount(each, tagMappingByTagIdMap));
        return rootTags;
    }

    private QuestionTagResp convertToQuestionTagResp(QuestionTagInfoDO tag) {
        return QuestionTagResp.builder()
                .id(tag.getId())
                .tagId(tag.getTagId())
                .tagName(tag.getTagName())
                .tagDesc(tag.getTagDesc())
                .tagLevel(tag.getTagLevel())
                .parentId(tag.getParentId())
                .type(tag.getType())
                .count(0)
                .child(new ArrayList<>())
                .build();
    }

    private void calculateChildCount(QuestionTagResp tag, Map<String, List<QuestionTagMappingDO>> tagMappingByTagIdMap) {
        if (tag.getChild() == null || tag.getChild().isEmpty()) {
            tag.setCount(0);
        } else {
            int childCount = tag.getChild().size();
            for (QuestionTagResp child : tag.getChild()) {
                calculateChildCount(child, tagMappingByTagIdMap);
                childCount += child.getCount();
                if (tagMappingByTagIdMap.containsKey(child.getTagId())) {
                    child.setQuestionCount(tagMappingByTagIdMap.get(child.getTagId()).size());
                } else {
                    child.setQuestionCount(0);
                }
            }
            tag.setCount(childCount);
        }
    }

    @Override
    public QuestionTagContainQuestionResp listContainQuestion(String tagId) {
        QuestionTagInfoDO tagInfoDO = getByTagId(tagId);
        if (ObjectUtil.isNotNull(tagInfoDO)) {
            QuestionTagContainQuestionResp containQuestionResp = BeanUtils.toBean(tagInfoDO, QuestionTagContainQuestionResp.class);
        }
        return null;
    }
}
