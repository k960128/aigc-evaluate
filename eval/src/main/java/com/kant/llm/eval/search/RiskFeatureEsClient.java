package com.kant.llm.eval.search;

import com.kant.llm.eval.dao.entity.RiskAttackFeatureDO;

import java.util.List;

/**
 * L2 攻击特征 ES 检索客户端。
 *
 * <p>该接口隐藏真实 ES 客户端细节，方便后续替换索引名称、查询 DSL 或接入其他关键词检索实现。
 * 调用方负责在失败时做降级，避免单路召回异常阻断整个 L2 主流程。</p>
 */
public interface RiskFeatureEsClient {

    /**
     * 搜索攻击特征。
     *
     * @param queryText 用户样本和模型输出拼接后的召回文本
     * @param topK 最大返回数量，非正数时由实现使用默认值
     * @return ES 召回命中列表，分数已在实现内归一化
     */
    List<RiskFeatureEsHit> search(String queryText, int topK);

    /**
     * 新增或覆盖攻击特征文档。
     *
     * @param feature MySQL 中的攻击特征源记录
     */
    void upsertFeature(RiskAttackFeatureDO feature);

    /**
     * 删除攻击特征文档。
     *
     * @param featureId 攻击特征 ID，也是 ES document id
     */
    void deleteFeature(Long featureId);
}
