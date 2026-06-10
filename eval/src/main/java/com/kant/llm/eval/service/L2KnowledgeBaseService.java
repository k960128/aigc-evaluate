package com.kant.llm.eval.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kant.llm.eval.dto.req.L2KbSyncEventPageRequest;
import com.kant.llm.eval.dto.req.L2RiskAttackFeaturePageRequest;
import com.kant.llm.eval.dto.req.L2RiskDetailRulePageRequest;
import com.kant.llm.eval.dto.req.SaveL2RiskAttackFeatureRequest;
import com.kant.llm.eval.dto.req.SaveL2RiskDetailRuleRequest;
import com.kant.llm.eval.dto.req.UpdateL2KbSyncEventStatusRequest;
import com.kant.llm.eval.dto.resp.L2KbSyncEventVO;
import com.kant.llm.eval.dto.resp.L2RiskAttackFeatureVO;
import com.kant.llm.eval.dto.resp.L2RiskDetailRuleVO;

/**
 * L2 知识库管理服务。
 *
 * <p>该接口承载 L2 知识库的“唯一事实源维护”能力：
 * Controller 不直接操作 Mapper，而是通过这里完成规则/特征变更、同步事件写入和同步状态回写。</p>
 */
public interface L2KnowledgeBaseService {

    /**
     * 分页查询风险小类判定规则。
     *
     * <p>返回结果会补齐 risk_category/risk_details 的中文名称，便于后台直接展示。</p>
     */
    Page<L2RiskDetailRuleVO> pageDetailRule(L2RiskDetailRulePageRequest request);

    /**
     * 根据 ID 查询风险小类判定规则。
     *
     * <p>不存在时返回 null，由接口层保持统一成功响应语义。</p>
     */
    L2RiskDetailRuleVO getDetailRule(Long id);

    /**
     * 创建风险小类判定规则。
     *
     * <p>每个 riskDetailsId 只允许一条有效规则，创建成功后写入 DETAIL_RULE/CREATE 同步事件。</p>
     */
    L2RiskDetailRuleVO createDetailRule(SaveL2RiskDetailRuleRequest request);

    /**
     * 更新风险小类判定规则。
     *
     * <p>更新会递增版本号，并写入 DETAIL_RULE/UPDATE 同步事件。</p>
     */
    L2RiskDetailRuleVO updateDetailRule(SaveL2RiskDetailRuleRequest request);

    /**
     * 启用或禁用风险小类判定规则。
     *
     * <p>启停也会影响知识库可用性，因此写入 DETAIL_RULE/REINDEX 同步事件。</p>
     */
    Boolean updateDetailRuleStatus(Long id, Integer status, String updater);

    /**
     * 删除风险小类判定规则。
     *
     * <p>删除使用逻辑删除，并写入 DETAIL_RULE/DELETE 同步事件。</p>
     */
    Boolean deleteDetailRule(Long id);

    /**
     * 分页查询攻击特征。
     *
     * <p>用于后台维护特征和排查 ES/Milvus 同步状态。</p>
     */
    Page<L2RiskAttackFeatureVO> pageAttackFeature(L2RiskAttackFeaturePageRequest request);

    /**
     * 根据 ID 查询攻击特征。
     *
     * <p>返回 contentHash、版本和同步状态，便于定位单条特征的索引状态。</p>
     */
    L2RiskAttackFeatureVO getAttackFeature(Long id);

    /**
     * 创建攻击特征。
     *
     * <p>创建成功后将同步状态置为待同步，并写入 ATTACK_FEATURE/CREATE 事件。</p>
     */
    L2RiskAttackFeatureVO createAttackFeature(SaveL2RiskAttackFeatureRequest request);

    /**
     * 更新攻击特征。
     *
     * <p>更新成功后重新计算 contentHash、递增版本并写入 ATTACK_FEATURE/UPDATE 事件。</p>
     */
    L2RiskAttackFeatureVO updateAttackFeature(SaveL2RiskAttackFeatureRequest request);

    /**
     * 启用或禁用攻击特征。
     *
     * <p>启停会影响 L2 召回可见性，因此重置同步状态并写入 ATTACK_FEATURE/REINDEX 事件。</p>
     */
    Boolean updateAttackFeatureStatus(Long id, Integer status, String updater);

    /**
     * 删除攻击特征。
     *
     * <p>删除前先标记 DELETE_PENDING 并写入 ATTACK_FEATURE/DELETE 事件，再执行逻辑删除。</p>
     */
    Boolean deleteAttackFeature(Long id);

    /**
     * 分页查询同步事件。
     *
     * <p>用于观察知识库变更是否已被 ES/Milvus 消费。</p>
     */
    Page<L2KbSyncEventVO> pageSyncEvent(L2KbSyncEventPageRequest request);

    /**
     * 更新同步事件状态，并同步回写攻击特征索引状态。
     *
     * <p>真实索引器后续写入 ES/Milvus 后，可以通过该方法回填成功或失败状态。</p>
     */
    Boolean updateSyncEventStatus(UpdateL2KbSyncEventStatusRequest request);
}
