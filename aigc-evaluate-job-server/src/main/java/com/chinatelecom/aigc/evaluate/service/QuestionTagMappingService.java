package com.chinatelecom.aigc.evaluate.service;

import com.chinatelecom.aigc.evaluate.domain.QuestionDO;
import com.chinatelecom.aigc.evaluate.domain.QuestionTagMappingDO;

import java.util.ArrayList;
import java.util.List;

public interface QuestionTagMappingService {

    /**
     * 创建标签关联映射关系
     * @param questionID 题目唯一ID
     * @param tags 标签集合
     */
    void createMapping(String questionID, String tags);

    /**
     * 删除标签
     * @param questionId 题目唯一ID
     */
    void deleteMapping(String questionId);

    /**
     * 批量创建标签
     * @param questionDOS list
     */
    void batchCreateMapping(List<QuestionDO> questionDOS);

    /**
     * 根据标签ID集合
     * @param tagIds
     * @return
     */
    List<QuestionTagMappingDO> listByTagIds(ArrayList<String> tagIds);
}
