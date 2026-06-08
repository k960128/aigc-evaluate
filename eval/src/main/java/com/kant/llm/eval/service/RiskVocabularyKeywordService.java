package com.kant.llm.eval.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.kant.llm.eval.dao.entity.RiskVocabularyKeywordDO;

public interface RiskVocabularyKeywordService extends IService<RiskVocabularyKeywordDO> {

    /**
     * 发布当前全量有效特征词快照，并通知集群节点异步构建 AC 自动机。
     */
    String publishAcSnapshot();
}
