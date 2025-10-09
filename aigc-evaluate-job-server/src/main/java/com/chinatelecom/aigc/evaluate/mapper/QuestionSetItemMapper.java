package com.chinatelecom.aigc.evaluate.mapper;
import com.chinatelecom.aigc.evaluate.domain.QuestionSetItemDO;
import com.chinatelecom.aigc.evaluate.framework.mybatis.core.mapper.BaseMapperX;
import com.chinatelecom.aigc.evaluate.framework.mybatis.core.query.LambdaQueryWrapperX;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface QuestionSetItemMapper extends BaseMapperX<QuestionSetItemDO> {

    default List<QuestionSetItemDO> selectListByQuestionSetIds(List<Long> questionSetIds) {
        return selectList(new LambdaQueryWrapperX<QuestionSetItemDO>()
                .in(QuestionSetItemDO::getQuestionSetId, questionSetIds)
                .orderByAsc(QuestionSetItemDO::getQuestionId));
    }
}
