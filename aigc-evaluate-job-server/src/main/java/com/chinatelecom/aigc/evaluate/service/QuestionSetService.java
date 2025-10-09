package com.chinatelecom.aigc.evaluate.service;

import com.chinatelecom.aigc.evaluate.common.pojo.PageResult;
import com.chinatelecom.aigc.evaluate.domain.QuestionSetInfoDO;
import com.chinatelecom.aigc.evaluate.dto.req.QuestionSetPageReq;
import com.chinatelecom.aigc.evaluate.dto.req.QuestionSetSaveReq;
import com.chinatelecom.aigc.evaluate.dto.req.QuestionSetUpdateReq;

public interface QuestionSetService {

    QuestionSetInfoDO create(QuestionSetSaveReq req);

    void delete(Long id);

    QuestionSetInfoDO get(Long id);

    /**
     * 是否包含习题列表
     * @param id 习题集ID
     * @param contain true|false
     * @return 习题集
     */
    QuestionSetInfoDO get(Long id,Boolean contain);

    PageResult<QuestionSetInfoDO> getQuestionSetPage(QuestionSetPageReq req);

    void update(QuestionSetUpdateReq req);

    void execute(Long id);
}
