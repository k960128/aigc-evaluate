package com.chinatelecom.aigc.evaluate.service;

import com.chinatelecom.aigc.evaluate.common.pojo.PageResult;
import com.chinatelecom.aigc.evaluate.dto.req.QuestionSetItemPageReq;
import com.chinatelecom.aigc.evaluate.dto.req.QuestionSetPageByReq;
import com.chinatelecom.aigc.evaluate.dto.resp.QuestionSetItemResp;
import com.chinatelecom.aigc.evaluate.dto.resp.QuestionSetItemStatisticsResp;

import java.util.List;
import java.util.Map;

public interface QuestionSetItemService {
    List<QuestionSetItemResp> list(List<Long> ids, Boolean distinct);

    PageResult<QuestionSetItemResp> getQuestionSetItemPage(QuestionSetItemPageReq req);

    PageResult<QuestionSetItemResp> getQuestionByIdPage(QuestionSetPageByReq req);

    Integer getQuestionSetItemCountBySetId(Long setId);

    List<String> getQuestionIdBySetId(Long setId);

    List<String> getQuestionIdBySetIdGroupCategory(Long setId, String difficulty);

    Map<Long, Integer> getQuestionSetItemCountBySetIds(List<Long> setIds);

    List<QuestionSetItemStatisticsResp> getQuestionSetStatistics(List<Long> ids, Boolean distinct);

    PageResult<QuestionSetItemResp> searchBySetIdPage(QuestionSetPageByReq req);

    List<QuestionSetItemStatisticsResp> getQuestionSetStatistic(Long id, Boolean distinct);
}
