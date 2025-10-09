package com.chinatelecom.aigc.evaluate.service;

import com.chinatelecom.aigc.evaluate.domain.QuestionTagInfoDO;
import com.chinatelecom.aigc.evaluate.dto.req.QuestionTagReq;
import com.chinatelecom.aigc.evaluate.dto.req.QuestionTagSaveReq;
import com.chinatelecom.aigc.evaluate.dto.req.QuestionTagUpdateReq;
import com.chinatelecom.aigc.evaluate.dto.resp.QuestionTagContainQuestionResp;
import com.chinatelecom.aigc.evaluate.dto.resp.QuestionTagResp;

import java.util.List;

public interface QuestionTagService {
    void create(QuestionTagSaveReq param);

    void update(QuestionTagUpdateReq param);

    QuestionTagInfoDO getByTagId(String tagId);

    QuestionTagResp getByTagIdContainChild(String tagId);

    List<QuestionTagResp> list(QuestionTagReq param);

    List<QuestionTagResp> listTree();

    QuestionTagContainQuestionResp listContainQuestion(String tagId);
}
