package com.chinatelecom.aigc.evaluate.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chinatelecom.aigc.evaluate.common.pojo.PageResult;
import com.chinatelecom.aigc.evaluate.domain.QuestionDO;
import com.chinatelecom.aigc.evaluate.dto.model.QuestionKey;
import com.chinatelecom.aigc.evaluate.dto.req.QuestionExportReq;
import com.chinatelecom.aigc.evaluate.dto.req.QuestionPageExportReq;
import com.chinatelecom.aigc.evaluate.dto.req.QuestionPageReq;
import com.chinatelecom.aigc.evaluate.framework.mybatis.core.mapper.BaseMapperX;
import com.chinatelecom.aigc.evaluate.framework.mybatis.core.query.LambdaQueryWrapperX;
import io.swagger.v3.oas.annotations.Parameter;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Collection;
import java.util.List;
import java.util.Set;

@Mapper
public interface QuestionMapper extends BaseMapperX<QuestionDO> {

    /**
     * 删除题目
     *
     * @param questionDO 题目
     * @return 影响行数
     */
    int deleteByQuestionId(@Parameter QuestionDO questionDO);

    /**
     * 条件分页查询
     *
     * @param questionPageReq
     * @return
     */
    default PageResult<QuestionDO> selectPage(QuestionPageReq questionPageReq) {
        return selectPage(questionPageReq, new LambdaQueryWrapperX<QuestionDO>()
                .likeIfPresent(QuestionDO::getTitle, questionPageReq.getTitle())
                .eqIfPresent(QuestionDO::getDifficulty, questionPageReq.getDifficulty())
                .likeIfPresent(QuestionDO::getCategory, questionPageReq.getCategory())
                .likeIfPresent(QuestionDO::getAttackMethod, questionPageReq.getAttackMethod())
                .likeIfPresent(QuestionDO::getDataSource,questionPageReq.getDataSource())
                .orderByDesc(QuestionDO::getUpdateTime, QuestionDO::getId));
    }

    default PageResult<QuestionDO> selectPage(QuestionPageExportReq pageExportReq) {
        return selectPage(pageExportReq, new LambdaQueryWrapperX<QuestionDO>()
                .inIfPresent(QuestionDO::getQuestionId, pageExportReq.getQuestionIds()));
    }

    /**
     * 条件分页查询包含标签
     *
     * @param questionPageReq
     * @return
     */
    default PageResult<QuestionDO> selectPageContainTag(QuestionPageReq questionPageReq, Set<String> questionIds) {
        return selectPage(questionPageReq, new LambdaQueryWrapperX<QuestionDO>()
                .likeIfPresent(QuestionDO::getTitle, questionPageReq.getTitle())
                .eqIfPresent(QuestionDO::getDifficulty, questionPageReq.getDifficulty())
                .likeIfPresent(QuestionDO::getCategory, questionPageReq.getCategory())
                .likeIfPresent(QuestionDO::getAttackMethod, questionPageReq.getAttackMethod())
                .inIfPresent(QuestionDO::getQuestionId, questionIds)
                .likeIfPresent(QuestionDO::getDataSource, questionPageReq.getDataSource())
                .orderByDesc(QuestionDO::getUpdateTime, QuestionDO::getId));
    }

    int updateByQuestionId(@Parameter QuestionDO updateDO);

    default QuestionDO selectByQuestionId(String questionId) {
        return selectOne(new LambdaQueryWrapperX<QuestionDO>()
                .eq(QuestionDO::getQuestionId, questionId));
    }


    default List<QuestionDO> selectList(List<String> questionIds) {
        return selectList(new LambdaQueryWrapperX<QuestionDO>()
                .in(QuestionDO::getQuestionId, questionIds));
    }

    int deleteBatchByQuestionId(@Parameter List<String> questionIds);

    default Integer selectCountByQuestionIds(List<String> questionIds) {
        return Math.toIntExact(selectCount(new LambdaQueryWrapperX<QuestionDO>()
                .inIfPresent(QuestionDO::getQuestionId, questionIds)));
    }

    List<QuestionDO> selectLatestVersions(@Param("collection") Collection<Long> questionIdSet);

    List<QuestionDO> selectLatestQuestionBatch(@Param("keyList") List<QuestionKey> keyList);

    List<QuestionDO> selectSnapshotQuestionBatch(@Param("keyList") List<QuestionKey> keyList);

}
