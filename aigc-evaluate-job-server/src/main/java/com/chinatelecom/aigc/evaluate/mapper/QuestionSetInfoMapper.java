package com.chinatelecom.aigc.evaluate.mapper;

import com.chinatelecom.aigc.evaluate.common.pojo.PageResult;
import com.chinatelecom.aigc.evaluate.domain.QuestionSetInfoDO;
import com.chinatelecom.aigc.evaluate.dto.req.QuestionSetPageReq;
import com.chinatelecom.aigc.evaluate.framework.mybatis.core.mapper.BaseMapperX;
import com.chinatelecom.aigc.evaluate.framework.mybatis.core.query.LambdaQueryWrapperX;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface QuestionSetInfoMapper extends BaseMapperX<QuestionSetInfoDO> {
    /**
     * 条件分页查询
     *
     * @param req
     * @return
     */
    default PageResult<QuestionSetInfoDO> selectPage(QuestionSetPageReq req) {
        return selectPage(req, new LambdaQueryWrapperX<QuestionSetInfoDO>()
                .likeIfPresent(QuestionSetInfoDO::getQuestionSetName, req.getQuestionSetName())
                .likeIfPresent(QuestionSetInfoDO::getQuestionCategory, req.getQuestionCategory())
                .orderByDesc(QuestionSetInfoDO::getCreateTime)
                .orderByDesc(QuestionSetInfoDO::getUpdateTime));
    }
}
