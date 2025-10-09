package com.chinatelecom.aigc.evaluate.service;

import com.chinatelecom.aigc.evaluate.common.pojo.PageResult;
import com.chinatelecom.aigc.evaluate.domain.QuestionDO;
import com.chinatelecom.aigc.evaluate.dto.req.QuestionExportReq;
import com.chinatelecom.aigc.evaluate.dto.req.QuestionPageReq;
import com.chinatelecom.aigc.evaluate.dto.req.QuestionSaveReq;
import com.chinatelecom.aigc.evaluate.dto.req.QuestionUpdateReq;
import com.chinatelecom.aigc.evaluate.dto.resp.QuestionBatchResp;
import com.chinatelecom.aigc.evaluate.dto.resp.QuestionImportResp;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.util.List;

public interface QuestionService {
    /**
     * 批量导入题目
     *
     * @param file     导入文件
     * @return 导入结果
     */
    QuestionImportResp importQuestionList(MultipartFile file);

    /**
     * 创建题目
     * @param req
     * @return
     */
    QuestionDO create(QuestionSaveReq req);

    /**
     * 修改题目
     * @param req
     */
    int update(QuestionUpdateReq req);

    /**
     * 根据questionId获取题目
     * @param questionId
     * @return
     */
    QuestionDO getByQuestion(String questionId);

    /**
     * 删除题目
     * @param questionId
     */
    int delete(String questionId);

    /**
     * 批量删除
     * @param questionIds
     * @return
     */
    QuestionBatchResp batchDelete(List<String> questionIds);

    /**
     * 分页查询
     * @param questionPageReq
     * @return
     */
    PageResult<QuestionDO> getQuestionPage(QuestionPageReq questionPageReq);

    /**
     * 获取所有的题目集合
     * @return
     */
    List<QuestionDO> getQuestionAll();

    /**
     * 导出题目
     * @param questionExportReq
     * @param response
     */
    void exportExcel(QuestionExportReq questionExportReq, HttpServletResponse response);
}
