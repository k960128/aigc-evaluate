package com.chinatelecom.aigc.evaluate.service;

import com.chinatelecom.aigc.evaluate.domain.ImportQuestionLogDO;

public interface ImportQuestionLogService {
    /**
     * 是否存在运行中的任务
     * @return boolean
     */
    Boolean getRunImportTask();

    /**
     * 新增导入日志
     * @param importQuestionLogDO do
     * @return bean
     */
    ImportQuestionLogDO create(ImportQuestionLogDO importQuestionLogDO);

    /**
     * 修改导入日志
     * @param importQuestionLogDO do
     */
    void update(ImportQuestionLogDO importQuestionLogDO);
}
