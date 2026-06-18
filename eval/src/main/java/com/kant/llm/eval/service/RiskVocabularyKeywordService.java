package com.kant.llm.eval.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.kant.llm.eval.dao.entity.RiskVocabularyKeywordDO;

public interface RiskVocabularyKeywordService extends IService<RiskVocabularyKeywordDO> {

    /**
     * 发布当前 AC 快照。
     *
     * <p>发布阶段只写入 Redis 清单和分片，并发送 RocketMQ 构建广播；
     * Redis 最新版本指针由协调器在目标节点完成构建确认后再切换。</p>
     */
    String publishAcSnapshot();
}
