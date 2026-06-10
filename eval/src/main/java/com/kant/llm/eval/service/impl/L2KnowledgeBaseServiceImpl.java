package com.kant.llm.eval.service.impl;

import cn.hutool.core.util.IdUtil;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kant.llm.eval.common.enums.KbSyncStatusEnums;
import com.kant.llm.eval.common.exception.ServiceException;
import com.kant.llm.eval.dao.entity.KbSyncEventDO;
import com.kant.llm.eval.dao.entity.RiskAttackFeatureDO;
import com.kant.llm.eval.dao.entity.RiskCategoryDO;
import com.kant.llm.eval.dao.entity.RiskDetailRuleDO;
import com.kant.llm.eval.dao.entity.RiskDetailsDO;
import com.kant.llm.eval.dao.mapper.KbSyncEventMapper;
import com.kant.llm.eval.dao.mapper.RiskAttackFeatureMapper;
import com.kant.llm.eval.dao.mapper.RiskCategoryMapper;
import com.kant.llm.eval.dao.mapper.RiskDetailRuleMapper;
import com.kant.llm.eval.dao.mapper.RiskDetailsMapper;
import com.kant.llm.eval.dto.req.L2KbSyncEventPageRequest;
import com.kant.llm.eval.dto.req.L2RiskAttackFeaturePageRequest;
import com.kant.llm.eval.dto.req.L2RiskDetailRulePageRequest;
import com.kant.llm.eval.dto.req.SaveL2RiskAttackFeatureRequest;
import com.kant.llm.eval.dto.req.SaveL2RiskDetailRuleRequest;
import com.kant.llm.eval.dto.req.UpdateL2KbSyncEventStatusRequest;
import com.kant.llm.eval.dto.resp.L2KbSyncEventVO;
import com.kant.llm.eval.dto.resp.L2RiskAttackFeatureVO;
import com.kant.llm.eval.dto.resp.L2RiskDetailRuleVO;
import com.kant.llm.eval.service.L2KnowledgeBaseService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * L2 知识库管理服务实现。
 *
 * <p>该服务把规则/特征维护和同步事件写入集中在一个事务边界内，
 * 确保 MySQL 事实源变更后一定留下可追踪的索引同步事件。</p>
 */
@Service
public class L2KnowledgeBaseServiceImpl implements L2KnowledgeBaseService {

    /** 同步事件聚合类型：攻击特征，后续索引侧据此写入或删除召回索引。 */
    private static final String AGGREGATE_ATTACK_FEATURE = "ATTACK_FEATURE";

    /** 同步事件聚合类型：风险小类规则，后续可用于同步规则说明、严重等级等辅助信息。 */
    private static final String AGGREGATE_DETAIL_RULE = "DETAIL_RULE";

    /** 同步事件操作类型：新增知识库数据。 */
    private static final String OPERATION_CREATE = "CREATE";

    /** 同步事件操作类型：编辑已有知识库数据。 */
    private static final String OPERATION_UPDATE = "UPDATE";

    /** 同步事件操作类型：删除知识库数据。 */
    private static final String OPERATION_DELETE = "DELETE";

    /** 同步事件操作类型：数据本身存在但可见性变化，需要索引侧重新处理。 */
    private static final String OPERATION_REINDEX = "REINDEX";

    /** 分页查询默认页码，避免前端不传分页参数时一次性扫全表。 */
    private static final int DEFAULT_PAGE_CURRENT = 1;

    /** 分页查询默认页大小，和已有后台接口的轻量分页习惯保持一致。 */
    private static final int DEFAULT_PAGE_SIZE = 10;

    private final RiskDetailRuleMapper riskDetailRuleMapper;

    private final RiskAttackFeatureMapper riskAttackFeatureMapper;

    private final KbSyncEventMapper kbSyncEventMapper;

    private final RiskDetailsMapper riskDetailsMapper;

    private final RiskCategoryMapper riskCategoryMapper;

    public L2KnowledgeBaseServiceImpl(RiskDetailRuleMapper riskDetailRuleMapper,
                                      RiskAttackFeatureMapper riskAttackFeatureMapper,
                                      KbSyncEventMapper kbSyncEventMapper,
                                      RiskDetailsMapper riskDetailsMapper,
                                      RiskCategoryMapper riskCategoryMapper) {
        this.riskDetailRuleMapper = riskDetailRuleMapper;
        this.riskAttackFeatureMapper = riskAttackFeatureMapper;
        this.kbSyncEventMapper = kbSyncEventMapper;
        this.riskDetailsMapper = riskDetailsMapper;
        this.riskCategoryMapper = riskCategoryMapper;
    }

    @Override
    public Page<L2RiskDetailRuleVO> pageDetailRule(L2RiskDetailRulePageRequest request) {
        // 查询请求允许为空，空请求表示走默认分页并不附加筛选条件。
        L2RiskDetailRulePageRequest queryRequest = request == null ? new L2RiskDetailRulePageRequest() : request;
        LambdaQueryWrapper<RiskDetailRuleDO> queryWrapper = new LambdaQueryWrapper<>();
        if (queryRequest.getRiskDetailsId() != null) {
            queryWrapper.eq(RiskDetailRuleDO::getRiskDetailsId, queryRequest.getRiskDetailsId());
        }
        if (queryRequest.getSeverityLevel() != null) {
            queryWrapper.eq(RiskDetailRuleDO::getSeverityLevel, queryRequest.getSeverityLevel());
        }
        if (queryRequest.getStatus() != null) {
            queryWrapper.eq(RiskDetailRuleDO::getStatus, queryRequest.getStatus());
        }
        applyCategoryFilter(queryWrapper, queryRequest.getCategoryId());
        queryWrapper.orderByAsc(RiskDetailRuleDO::getRiskDetailsId);

        // 先分页查询规则主表，再批量补齐大类/小类名称，避免每条记录循环查询数据库。
        Page<RiskDetailRuleDO> pageResult = riskDetailRuleMapper.selectPage(
                new Page<>(pageCurrent(queryRequest.getCurrent()), pageSize(queryRequest.getSize())), queryWrapper);
        List<RiskDetailRuleDO> records = pageResult.getRecords();
        Map<Long, RiskDetailsDO> detailMap = buildDetailMap(records.stream()
                .map(RiskDetailRuleDO::getRiskDetailsId)
                .collect(Collectors.toSet()));
        Map<Long, RiskCategoryDO> categoryMap = buildCategoryMap(detailMap.values().stream()
                .map(RiskDetailsDO::getCategoryId)
                .collect(Collectors.toSet()));
        Page<L2RiskDetailRuleVO> voPage = new Page<>(pageResult.getCurrent(), pageResult.getSize(), pageResult.getTotal());
        voPage.setRecords(records.stream()
                .map(rule -> convertToRuleVO(rule, detailMap.get(rule.getRiskDetailsId()), categoryMap))
                .toList());
        return voPage;
    }

    @Override
    public L2RiskDetailRuleVO getDetailRule(Long id) {
        // 详情接口不存在时返回 null，保持查询类接口不抛业务异常的温和语义。
        RiskDetailRuleDO rule = riskDetailRuleMapper.selectById(id);
        if (rule == null) {
            return null;
        }
        RiskDetailsDO detail = riskDetailsMapper.selectById(rule.getRiskDetailsId());
        Map<Long, RiskCategoryDO> categoryMap = detail == null ? Map.of() : buildCategoryMap(singletonNullable(detail.getCategoryId()));
        return convertToRuleVO(rule, detail, categoryMap);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public L2RiskDetailRuleVO createDetailRule(SaveL2RiskDetailRuleRequest request) {
        requireNonNull(request, "规则创建请求不能为空");
        // risk_details 是风险分类体系的来源表，规则必须挂在一个已存在的小类上。
        RiskDetailsDO detail = requireRiskDetails(request.getRiskDetailsId());
        RiskDetailRuleDO rule = new RiskDetailRuleDO();
        fillRule(rule, request, false);
        rule.setRiskDetailsId(detail.getId());
        // 每个风险小类只维护一条规则，避免 L2 聚合到 riskDetailsId 后出现多套判定口径。
        ensureUniqueDetailRule(rule.getRiskDetailsId(), null);
        rule.setVersion(1);
        rule.setStatus(defaultInt(request.getStatus(), 1));
        riskDetailRuleMapper.insert(rule);
        // MySQL 写入和同步事件写入在同一事务内完成，保证事实源变更一定可被后续索引器感知。
        createSyncEvent(AGGREGATE_DETAIL_RULE, rule.getId(), OPERATION_CREATE, rule.getRiskDetailsId(),
                null, rule.getVersion(), rule);
        return getDetailRule(rule.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public L2RiskDetailRuleVO updateDetailRule(SaveL2RiskDetailRuleRequest request) {
        requireNonNull(request, "规则更新请求不能为空");
        requireNonNull(request.getId(), "规则 ID 不能为空");
        RiskDetailRuleDO current = requireDetailRule(request.getId());
        if (request.getRiskDetailsId() != null) {
            requireRiskDetails(request.getRiskDetailsId());
        }
        // 更新接口支持局部字段更新：请求里为 null 的字段保留数据库原值。
        fillRule(current, request, true);
        ensureUniqueDetailRule(current.getRiskDetailsId(), current.getId());
        // 版本号用于同步事件排序和后续索引侧幂等判断。
        current.setVersion(nextVersion(current.getVersion()));
        riskDetailRuleMapper.updateById(current);
        createSyncEvent(AGGREGATE_DETAIL_RULE, current.getId(), OPERATION_UPDATE, current.getRiskDetailsId(),
                null, current.getVersion(), current);
        return getDetailRule(current.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean updateDetailRuleStatus(Long id, Integer status, String updater) {
        RiskDetailRuleDO rule = requireDetailRule(id);
        rule.setStatus(requireStatus(status));
        rule.setUpdater(updater);
        rule.setVersion(nextVersion(rule.getVersion()));
        riskDetailRuleMapper.updateById(rule);
        // 启停不改变规则正文，但会改变规则是否生效，因此用 REINDEX 表示需要索引侧重新处理该聚合。
        createSyncEvent(AGGREGATE_DETAIL_RULE, rule.getId(), OPERATION_REINDEX, rule.getRiskDetailsId(),
                null, rule.getVersion(), rule);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean deleteDetailRule(Long id) {
        RiskDetailRuleDO rule = requireDetailRule(id);
        // 这里依赖 MyBatis Plus 的逻辑删除能力，真实数据保留在表中，便于审计和回溯。
        riskDetailRuleMapper.deleteById(id);
        createSyncEvent(AGGREGATE_DETAIL_RULE, rule.getId(), OPERATION_DELETE, rule.getRiskDetailsId(),
                null, nextVersion(rule.getVersion()), rule);
        return true;
    }

    @Override
    public Page<L2RiskAttackFeatureVO> pageAttackFeature(L2RiskAttackFeaturePageRequest request) {
        // 攻击特征列表是知识库维护和同步排障的主要入口，因此支持多个维度组合筛选。
        L2RiskAttackFeaturePageRequest queryRequest = request == null ? new L2RiskAttackFeaturePageRequest() : request;
        LambdaQueryWrapper<RiskAttackFeatureDO> queryWrapper = new LambdaQueryWrapper<>();
        if (queryRequest.getCategoryId() != null) {
            queryWrapper.eq(RiskAttackFeatureDO::getCategoryId, queryRequest.getCategoryId());
        }
        if (queryRequest.getRiskDetailsId() != null) {
            queryWrapper.eq(RiskAttackFeatureDO::getRiskDetailsId, queryRequest.getRiskDetailsId());
        }
        if (StringUtils.isNotBlank(queryRequest.getKeyword())) {
            queryWrapper.like(RiskAttackFeatureDO::getFeatureText, queryRequest.getKeyword());
        }
        if (StringUtils.isNotBlank(queryRequest.getFeatureType())) {
            queryWrapper.eq(RiskAttackFeatureDO::getFeatureType, queryRequest.getFeatureType());
        }
        if (StringUtils.isNotBlank(queryRequest.getPolarity())) {
            queryWrapper.eq(RiskAttackFeatureDO::getPolarity, queryRequest.getPolarity());
        }
        if (queryRequest.getSyncStatus() != null) {
            queryWrapper.eq(RiskAttackFeatureDO::getSyncStatus, queryRequest.getSyncStatus());
        }
        if (queryRequest.getEsSyncStatus() != null) {
            queryWrapper.eq(RiskAttackFeatureDO::getEsSyncStatus, queryRequest.getEsSyncStatus());
        }
        if (queryRequest.getMilvusSyncStatus() != null) {
            queryWrapper.eq(RiskAttackFeatureDO::getMilvusSyncStatus, queryRequest.getMilvusSyncStatus());
        }
        if (queryRequest.getStatus() != null) {
            queryWrapper.eq(RiskAttackFeatureDO::getStatus, queryRequest.getStatus());
        }
        queryWrapper.orderByDesc(RiskAttackFeatureDO::getUpdateTime)
                .orderByDesc(RiskAttackFeatureDO::getId);

        // 与规则列表类似，先查主表分页，再批量补齐风险名称，避免 N+1 查询。
        Page<RiskAttackFeatureDO> pageResult = riskAttackFeatureMapper.selectPage(
                new Page<>(pageCurrent(queryRequest.getCurrent()), pageSize(queryRequest.getSize())), queryWrapper);
        List<RiskAttackFeatureDO> records = pageResult.getRecords();
        Map<Long, RiskDetailsDO> detailMap = buildDetailMap(records.stream()
                .map(RiskAttackFeatureDO::getRiskDetailsId)
                .collect(Collectors.toSet()));
        Map<Long, RiskCategoryDO> categoryMap = buildCategoryMap(records.stream()
                .map(RiskAttackFeatureDO::getCategoryId)
                .collect(Collectors.toSet()));
        Page<L2RiskAttackFeatureVO> voPage = new Page<>(pageResult.getCurrent(), pageResult.getSize(), pageResult.getTotal());
        voPage.setRecords(records.stream()
                .map(feature -> convertToFeatureVO(feature, detailMap.get(feature.getRiskDetailsId()), categoryMap))
                .toList());
        return voPage;
    }

    @Override
    public L2RiskAttackFeatureVO getAttackFeature(Long id) {
        // 详情接口返回同步状态和 contentHash，是排查“为什么没有命中召回”的第一入口。
        RiskAttackFeatureDO feature = riskAttackFeatureMapper.selectById(id);
        if (feature == null) {
            return null;
        }
        RiskDetailsDO detail = riskDetailsMapper.selectById(feature.getRiskDetailsId());
        Map<Long, RiskCategoryDO> categoryMap = buildCategoryMap(singletonNullable(feature.getCategoryId()));
        return convertToFeatureVO(feature, detail, categoryMap);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public L2RiskAttackFeatureVO createAttackFeature(SaveL2RiskAttackFeatureRequest request) {
        requireNonNull(request, "攻击特征创建请求不能为空");
        RiskDetailsDO detail = requireRiskDetails(request.getRiskDetailsId());
        RiskAttackFeatureDO feature = new RiskAttackFeatureDO();
        fillFeature(feature, request, detail, false);
        // contentHash 是特征去重和索引幂等更新的核心键，创建前必须检查重复。
        ensureUniqueFeatureHash(feature.getContentHash(), null);
        feature.setVersion(1);
        // 新增特征默认需要同步到 ES/Milvus；MySQL Mock 召回会直接读库，不依赖这两个状态。
        feature.setSyncStatus(KbSyncStatusEnums.PENDING.getCode());
        feature.setEsSyncStatus(KbSyncStatusEnums.PENDING.getCode());
        feature.setMilvusSyncStatus(KbSyncStatusEnums.PENDING.getCode());
        riskAttackFeatureMapper.insert(feature);
        createSyncEvent(AGGREGATE_ATTACK_FEATURE, feature.getId(), OPERATION_CREATE, feature.getRiskDetailsId(),
                feature.getContentHash(), feature.getVersion(), feature);
        return getAttackFeature(feature.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public L2RiskAttackFeatureVO updateAttackFeature(SaveL2RiskAttackFeatureRequest request) {
        requireNonNull(request, "攻击特征更新请求不能为空");
        requireNonNull(request.getId(), "攻击特征 ID 不能为空");
        RiskAttackFeatureDO current = requireAttackFeature(request.getId());
        // 更新时允许不传 riskDetailsId，不传则沿用原来的风险小类。
        RiskDetailsDO detail = request.getRiskDetailsId() == null
                ? requireRiskDetails(current.getRiskDetailsId())
                : requireRiskDetails(request.getRiskDetailsId());
        fillFeature(current, request, detail, true);
        ensureUniqueFeatureHash(current.getContentHash(), current.getId());
        current.setVersion(nextVersion(current.getVersion()));
        // 任意会影响召回内容、极性、权重或分类的更新，都需要重新进入同步队列。
        markFeaturePending(current);
        riskAttackFeatureMapper.updateById(current);
        createSyncEvent(AGGREGATE_ATTACK_FEATURE, current.getId(), OPERATION_UPDATE, current.getRiskDetailsId(),
                current.getContentHash(), current.getVersion(), current);
        return getAttackFeature(current.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean updateAttackFeatureStatus(Long id, Integer status, String updater) {
        RiskAttackFeatureDO feature = requireAttackFeature(id);
        feature.setStatus(requireStatus(status));
        feature.setUpdater(updater);
        feature.setVersion(nextVersion(feature.getVersion()));
        // 启停会直接影响 Mock 召回和真实索引召回的可见性，因此需要重置同步状态。
        markFeaturePending(feature);
        riskAttackFeatureMapper.updateById(feature);
        createSyncEvent(AGGREGATE_ATTACK_FEATURE, feature.getId(), OPERATION_REINDEX, feature.getRiskDetailsId(),
                feature.getContentHash(), feature.getVersion(), feature);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean deleteAttackFeature(Long id) {
        RiskAttackFeatureDO feature = requireAttackFeature(id);
        feature.setVersion(nextVersion(feature.getVersion()));
        // 删除前先把源数据状态改成 DELETE_PENDING，并把事件快照写入队列表。
        // 即使之后执行逻辑删除，索引侧仍能通过 kb_sync_event.payload 删除旧文档。
        feature.setSyncStatus(KbSyncStatusEnums.DELETE_PENDING.getCode());
        feature.setEsSyncStatus(KbSyncStatusEnums.PENDING.getCode());
        feature.setMilvusSyncStatus(KbSyncStatusEnums.PENDING.getCode());
        riskAttackFeatureMapper.updateById(feature);
        createSyncEvent(AGGREGATE_ATTACK_FEATURE, feature.getId(), OPERATION_DELETE, feature.getRiskDetailsId(),
                feature.getContentHash(), feature.getVersion(), feature);
        riskAttackFeatureMapper.deleteById(id);
        return true;
    }

    @Override
    public Page<L2KbSyncEventVO> pageSyncEvent(L2KbSyncEventPageRequest request) {
        // 同步事件列表面向“索引同步可观测”，因此支持按事件、聚合、操作和双索引状态筛选。
        L2KbSyncEventPageRequest queryRequest = request == null ? new L2KbSyncEventPageRequest() : request;
        LambdaQueryWrapper<KbSyncEventDO> queryWrapper = new LambdaQueryWrapper<>();
        if (StringUtils.isNotBlank(queryRequest.getEventId())) {
            queryWrapper.eq(KbSyncEventDO::getEventId, queryRequest.getEventId());
        }
        if (StringUtils.isNotBlank(queryRequest.getAggregateType())) {
            queryWrapper.eq(KbSyncEventDO::getAggregateType, queryRequest.getAggregateType());
        }
        if (queryRequest.getAggregateId() != null) {
            queryWrapper.eq(KbSyncEventDO::getAggregateId, queryRequest.getAggregateId());
        }
        if (StringUtils.isNotBlank(queryRequest.getOperationType())) {
            queryWrapper.eq(KbSyncEventDO::getOperationType, queryRequest.getOperationType());
        }
        if (queryRequest.getRiskDetailsId() != null) {
            queryWrapper.eq(KbSyncEventDO::getRiskDetailsId, queryRequest.getRiskDetailsId());
        }
        if (queryRequest.getEsStatus() != null) {
            queryWrapper.eq(KbSyncEventDO::getEsStatus, queryRequest.getEsStatus());
        }
        if (queryRequest.getMilvusStatus() != null) {
            queryWrapper.eq(KbSyncEventDO::getMilvusStatus, queryRequest.getMilvusStatus());
        }
        queryWrapper.orderByDesc(KbSyncEventDO::getCreateTime)
                .orderByDesc(KbSyncEventDO::getId);
        Page<KbSyncEventDO> pageResult = kbSyncEventMapper.selectPage(
                new Page<>(pageCurrent(queryRequest.getCurrent()), pageSize(queryRequest.getSize())), queryWrapper);
        Page<L2KbSyncEventVO> voPage = new Page<>(pageResult.getCurrent(), pageResult.getSize(), pageResult.getTotal());
        voPage.setRecords(pageResult.getRecords().stream()
                .map(this::convertToSyncEventVO)
                .toList());
        return voPage;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean updateSyncEventStatus(UpdateL2KbSyncEventStatusRequest request) {
        requireNonNull(request, "同步事件状态更新请求不能为空");
        if (StringUtils.isBlank(request.getEventId())) {
            throw new ServiceException("同步事件 ID 不能为空");
        }
        // eventId 是外部同步器可见的稳定 ID，不暴露数据库自增主键。
        KbSyncEventDO event = kbSyncEventMapper.selectOne(new LambdaQueryWrapper<KbSyncEventDO>()
                .eq(KbSyncEventDO::getEventId, request.getEventId())
                .last("LIMIT 1"));
        if (event == null) {
            throw new ServiceException("同步事件不存在");
        }
        // 支持只回写某一路索引状态：例如 ES 成功但 Milvus 仍在处理时，只传 esStatus 即可。
        if (request.getEsStatus() != null) {
            event.setEsStatus(requireIndexSyncStatus(request.getEsStatus()));
        }
        if (request.getMilvusStatus() != null) {
            event.setMilvusStatus(requireIndexSyncStatus(request.getMilvusStatus()));
        }
        event.setLastError(StringUtils.trimToNull(request.getLastError()));
        // 任一路失败都认为需要增加一次重试计数，后续同步 worker 可基于该字段做退避策略。
        event.setRetryCount(defaultInt(event.getRetryCount(), 0) + failedIncrement(request));
        kbSyncEventMapper.updateById(event);
        // 攻击特征事件需要把双索引状态聚合回特征表，便于列表页直接看到该特征的同步进度。
        syncBackAttackFeatureStatus(event);
        return true;
    }

    private void applyCategoryFilter(LambdaQueryWrapper<RiskDetailRuleDO> queryWrapper, Long categoryId) {
        if (categoryId == null) {
            return;
        }
        // risk_detail_rule 表只存 risk_details_id，不冗余 category_id。
        // 因此按大类筛选时，需要先从 risk_details 找到该大类下的小类 ID 列表。
        List<RiskDetailsDO> details = riskDetailsMapper.selectList(new LambdaQueryWrapper<RiskDetailsDO>()
                .select(RiskDetailsDO::getId)
                .eq(RiskDetailsDO::getCategoryId, categoryId));
        if (details.isEmpty()) {
            // 没有任何小类时追加一个不可能命中的条件，保证分页结果为空。
            queryWrapper.eq(RiskDetailRuleDO::getRiskDetailsId, -1L);
            return;
        }
        queryWrapper.in(RiskDetailRuleDO::getRiskDetailsId, details.stream().map(RiskDetailsDO::getId).toList());
    }

    private void fillRule(RiskDetailRuleDO rule, SaveL2RiskDetailRuleRequest request, boolean update) {
        // update=true 时只覆盖请求中显式传入的字段，避免局部编辑把已有内容清空。
        if (!update || request.getRiskDetailsId() != null) {
            rule.setRiskDetailsId(request.getRiskDetailsId());
        }
        if (!update || request.getJudgeRule() != null) {
            rule.setJudgeRule(StringUtils.trimToNull(request.getJudgeRule()));
        }
        if (!update || request.getSeverityLevel() != null) {
            rule.setSeverityLevel(defaultInt(request.getSeverityLevel(), 2));
        }
        if (!update || request.getDecisionBoundary() != null) {
            rule.setDecisionBoundary(StringUtils.trimToNull(request.getDecisionBoundary()));
        }
        if (!update || request.getUnsafeExamples() != null) {
            rule.setUnsafeExamples(StringUtils.trimToNull(request.getUnsafeExamples()));
        }
        if (!update || request.getSafeExamples() != null) {
            rule.setSafeExamples(StringUtils.trimToNull(request.getSafeExamples()));
        }
        if (request.getStatus() != null) {
            rule.setStatus(requireStatus(request.getStatus()));
        }
        if (request.getCreator() != null) {
            rule.setCreator(StringUtils.trimToNull(request.getCreator()));
        }
        if (request.getUpdater() != null) {
            rule.setUpdater(StringUtils.trimToNull(request.getUpdater()));
        }
        // 判定规则和风险小类是规则能否参与 L2 小类解释的最低要求。
        if (StringUtils.isBlank(rule.getJudgeRule())) {
            throw new ServiceException("判定规则不能为空");
        }
        if (rule.getRiskDetailsId() == null) {
            throw new ServiceException("风险小类 ID 不能为空");
        }
    }

    private void fillFeature(RiskAttackFeatureDO feature,
                             SaveL2RiskAttackFeatureRequest request,
                             RiskDetailsDO detail,
                             boolean update) {
        // 只要 featureText 变化，normalizedText 未显式传入时也要跟着刷新，避免 hash 仍基于旧文本。
        boolean featureTextChanged = request.getFeatureText() != null;
        if (!update || request.getRiskDetailsId() != null) {
            feature.setRiskDetailsId(detail.getId());
        }
        // categoryId 可以由前端显式传入；不传时以 risk_details.category_id 为准，保持分类体系一致。
        if (!update || request.getCategoryId() != null || feature.getCategoryId() == null) {
            feature.setCategoryId(request.getCategoryId() == null ? detail.getCategoryId() : request.getCategoryId());
        }
        if (!update || request.getFeatureCode() != null) {
            feature.setFeatureCode(StringUtils.trimToNull(request.getFeatureCode()));
        }
        if (!update || request.getFeatureText() != null) {
            feature.setFeatureText(StringUtils.trimToNull(request.getFeatureText()));
        }
        if (!update || request.getNormalizedText() != null || feature.getNormalizedText() == null || featureTextChanged) {
            String normalizedText = request.getNormalizedText() == null ? feature.getFeatureText() : request.getNormalizedText();
            feature.setNormalizedText(StringUtils.trimToNull(normalizedText));
        }
        if (!update || request.getFeatureType() != null) {
            feature.setFeatureType(StringUtils.trimToNull(request.getFeatureType()));
        }
        if (!update || request.getPolarity() != null) {
            feature.setPolarity(StringUtils.trimToNull(request.getPolarity()));
        }
        if (!update || request.getRiskLevel() != null) {
            feature.setRiskLevel(defaultInt(request.getRiskLevel(), 2));
        }
        if (!update || request.getLanguage() != null) {
            feature.setLanguage(StringUtils.defaultIfBlank(request.getLanguage(), "zh-CN"));
        }
        if (!update || request.getTags() != null) {
            feature.setTags(StringUtils.trimToNull(request.getTags()));
        }
        if (!update || request.getSource() != null) {
            feature.setSource(StringUtils.defaultIfBlank(request.getSource(), "manual"));
        }
        if (!update || request.getWeight() != null) {
            feature.setWeight(request.getWeight() == null ? BigDecimal.ONE : request.getWeight());
        }
        if (request.getStatus() != null) {
            feature.setStatus(requireStatus(request.getStatus()));
        } else if (!update) {
            feature.setStatus(1);
        }
        if (request.getCreator() != null) {
            feature.setCreator(StringUtils.trimToNull(request.getCreator()));
        }
        if (request.getUpdater() != null) {
            feature.setUpdater(StringUtils.trimToNull(request.getUpdater()));
        }
        if (StringUtils.isBlank(feature.getFeatureText())) {
            throw new ServiceException("攻击特征文本不能为空");
        }
        if (StringUtils.isBlank(feature.getFeatureType())) {
            throw new ServiceException("攻击特征类型不能为空");
        }
        if (StringUtils.isBlank(feature.getPolarity())) {
            throw new ServiceException("攻击特征极性不能为空");
        }
        // contentHash 不使用 featureCode，是为了让“同一小类、同一极性、同一归一化文本”天然幂等。
        // 这样前端重复提交或后续索引重复消费时，都能按 hash 识别同一条特征内容。
        feature.setContentHash(sha256(feature.getRiskDetailsId() + "|" + feature.getPolarity() + "|" + feature.getNormalizedText()));
    }

    private void createSyncEvent(String aggregateType,
                                 Long aggregateId,
                                 String operationType,
                                 Long riskDetailsId,
                                 String contentHash,
                                 Integer version,
                                 Object payload) {
        // 同步事件是后续真实 ES/Milvus worker 的输入队列。
        // payload 保存当时的完整对象快照，尤其是删除场景，源表逻辑删除后仍需要这份快照清理索引。
        KbSyncEventDO event = new KbSyncEventDO();
        event.setEventId(IdUtil.fastSimpleUUID());
        event.setAggregateType(aggregateType);
        event.setAggregateId(aggregateId);
        event.setOperationType(operationType);
        event.setRiskDetailsId(riskDetailsId);
        event.setContentHash(contentHash);
        event.setVersion(defaultInt(version, 1));
        event.setPayload(JSON.toJSONString(payload));
        event.setEsStatus(KbSyncStatusEnums.PENDING.getCode());
        event.setMilvusStatus(KbSyncStatusEnums.PENDING.getCode());
        event.setRetryCount(0);
        kbSyncEventMapper.insert(event);
    }

    private void syncBackAttackFeatureStatus(KbSyncEventDO event) {
        // 只有攻击特征真正对应 ES/Milvus 召回索引文档，规则事件当前只保留事件状态。
        if (!AGGREGATE_ATTACK_FEATURE.equals(event.getAggregateType()) || event.getAggregateId() == null) {
            return;
        }
        RiskAttackFeatureDO feature = riskAttackFeatureMapper.selectById(event.getAggregateId());
        if (feature == null) {
            return;
        }
        // 先同步两路原始状态，再计算综合状态，列表页可以同时展示细节和最终结论。
        feature.setEsSyncStatus(event.getEsStatus());
        feature.setMilvusSyncStatus(event.getMilvusStatus());
        if (Objects.equals(event.getEsStatus(), KbSyncStatusEnums.SYNCED.getCode())
                && Objects.equals(event.getMilvusStatus(), KbSyncStatusEnums.SYNCED.getCode())) {
            feature.setSyncStatus(KbSyncStatusEnums.SYNCED.getCode());
        } else if (Objects.equals(event.getEsStatus(), KbSyncStatusEnums.FAILED.getCode())
                || Objects.equals(event.getMilvusStatus(), KbSyncStatusEnums.FAILED.getCode())) {
            feature.setSyncStatus(KbSyncStatusEnums.FAILED.getCode());
        } else if (OPERATION_DELETE.equals(event.getOperationType())) {
            feature.setSyncStatus(KbSyncStatusEnums.DELETE_PENDING.getCode());
        } else {
            feature.setSyncStatus(KbSyncStatusEnums.PENDING.getCode());
        }
        riskAttackFeatureMapper.updateById(feature);
    }

    private void markFeaturePending(RiskAttackFeatureDO feature) {
        // 任意会影响召回可见性或召回内容的变更，都需要让 ES 和 Milvus 重新处理。
        feature.setSyncStatus(KbSyncStatusEnums.PENDING.getCode());
        feature.setEsSyncStatus(KbSyncStatusEnums.PENDING.getCode());
        feature.setMilvusSyncStatus(KbSyncStatusEnums.PENDING.getCode());
    }

    private void ensureUniqueDetailRule(Long riskDetailsId, Long excludeId) {
        // 风险小类规则按 riskDetailsId 唯一；更新时排除当前记录，防止误判自己重复。
        Long count = riskDetailRuleMapper.selectCount(new LambdaQueryWrapper<RiskDetailRuleDO>()
                .eq(RiskDetailRuleDO::getRiskDetailsId, riskDetailsId)
                .ne(excludeId != null, RiskDetailRuleDO::getId, excludeId));
        if (count != null && count > 0) {
            throw new ServiceException("该风险小类已存在判定规则");
        }
    }

    private void ensureUniqueFeatureHash(String contentHash, Long excludeId) {
        // 特征去重按 contentHash 做，而不是按 featureCode 做，因为 featureCode 偏人工维护，不适合幂等判断。
        Long count = riskAttackFeatureMapper.selectCount(new LambdaQueryWrapper<RiskAttackFeatureDO>()
                .eq(RiskAttackFeatureDO::getContentHash, contentHash)
                .ne(excludeId != null, RiskAttackFeatureDO::getId, excludeId));
        if (count != null && count > 0) {
            throw new ServiceException("攻击特征内容已存在，请勿重复维护");
        }
    }

    private RiskDetailsDO requireRiskDetails(Long riskDetailsId) {
        // 所有 L2 知识库数据都必须挂在既有风险小类上，不能绕开 risk_details 分类体系。
        requireNonNull(riskDetailsId, "风险小类 ID 不能为空");
        RiskDetailsDO detail = riskDetailsMapper.selectById(riskDetailsId);
        if (detail == null) {
            throw new ServiceException("风险小类不存在");
        }
        return detail;
    }

    private RiskDetailRuleDO requireDetailRule(Long id) {
        requireNonNull(id, "规则 ID 不能为空");
        RiskDetailRuleDO rule = riskDetailRuleMapper.selectById(id);
        if (rule == null) {
            throw new ServiceException("规则不存在");
        }
        return rule;
    }

    private RiskAttackFeatureDO requireAttackFeature(Long id) {
        requireNonNull(id, "攻击特征 ID 不能为空");
        RiskAttackFeatureDO feature = riskAttackFeatureMapper.selectById(id);
        if (feature == null) {
            throw new ServiceException("攻击特征不存在");
        }
        return feature;
    }

    private void requireNonNull(Object value, String message) {
        if (value == null) {
            throw new ServiceException(message);
        }
    }

    private Integer requireStatus(Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new ServiceException("状态只允许 0 或 1");
        }
        return status;
    }

    private Integer requireIndexSyncStatus(Integer status) {
        // DELETE_PENDING 是特征表上的综合状态，不允许外部作为 ES/Milvus 单路同步结果直接回写。
        if (status == null
                || (!status.equals(KbSyncStatusEnums.PENDING.getCode())
                && !status.equals(KbSyncStatusEnums.SYNCED.getCode())
                && !status.equals(KbSyncStatusEnums.FAILED.getCode()))) {
            throw new ServiceException("索引同步状态只允许 0、1、2");
        }
        return status;
    }

    private int failedIncrement(UpdateL2KbSyncEventStatusRequest request) {
        // 只要本次回写中任一路索引失败，就累计一次失败次数；未传的索引状态不参与判断。
        return Objects.equals(request.getEsStatus(), KbSyncStatusEnums.FAILED.getCode())
                || Objects.equals(request.getMilvusStatus(), KbSyncStatusEnums.FAILED.getCode())
                ? 1 : 0;
    }

    private int nextVersion(Integer version) {
        return defaultInt(version, 0) + 1;
    }

    private int defaultInt(Integer value, int defaultValue) {
        return value == null ? defaultValue : value;
    }

    private long pageCurrent(Integer current) {
        return current == null || current <= 0 ? DEFAULT_PAGE_CURRENT : current.longValue();
    }

    private long pageSize(Integer size) {
        return size == null || size <= 0 ? DEFAULT_PAGE_SIZE : size.longValue();
    }

    private Map<Long, RiskDetailsDO> buildDetailMap(Collection<Long> detailIds) {
        Set<Long> filteredIds = filterNonNullIds(detailIds);
        if (filteredIds.isEmpty()) {
            return Map.of();
        }
        // LinkedHashMap 保留数据库返回顺序，主要让调试和日志输出更稳定。
        return riskDetailsMapper.selectBatchIds(filteredIds).stream()
                .collect(Collectors.toMap(RiskDetailsDO::getId, Function.identity(), (left, right) -> left, LinkedHashMap::new));
    }

    private Map<Long, RiskCategoryDO> buildCategoryMap(Collection<Long> categoryIds) {
        Set<Long> filteredIds = filterNonNullIds(categoryIds);
        if (filteredIds.isEmpty()) {
            return Map.of();
        }
        // 批量加载风险大类名称，避免 VO 转换阶段出现 N+1 查询。
        return riskCategoryMapper.selectBatchIds(filteredIds).stream()
                .collect(Collectors.toMap(RiskCategoryDO::getId, Function.identity(), (left, right) -> left, LinkedHashMap::new));
    }

    private Set<Long> filterNonNullIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Set.of();
        }
        return ids.stream().filter(Objects::nonNull).collect(Collectors.toSet());
    }

    private List<Long> singletonNullable(Long value) {
        return value == null ? List.of() : List.of(value);
    }

    private L2RiskDetailRuleVO convertToRuleVO(RiskDetailRuleDO rule,
                                               RiskDetailsDO detail,
                                               Map<Long, RiskCategoryDO> categoryMap) {
        // 规则表不冗余大类名称，统一在 VO 层补齐，既保证展示友好，也避免主表数据漂移。
        RiskCategoryDO category = detail == null ? null : categoryMap.get(detail.getCategoryId());
        return L2RiskDetailRuleVO.builder()
                .id(rule.getId())
                .riskDetailsId(rule.getRiskDetailsId())
                .detailsName(detail == null ? null : detail.getDetailsName())
                .categoryId(detail == null ? null : detail.getCategoryId())
                .categoryName(category == null ? null : category.getCategoryName())
                .judgeRule(rule.getJudgeRule())
                .severityLevel(rule.getSeverityLevel())
                .decisionBoundary(rule.getDecisionBoundary())
                .unsafeExamples(rule.getUnsafeExamples())
                .safeExamples(rule.getSafeExamples())
                .version(rule.getVersion())
                .status(rule.getStatus())
                .creator(rule.getCreator())
                .updater(rule.getUpdater())
                .createTime(rule.getCreateTime())
                .updateTime(rule.getUpdateTime())
                .build();
    }

    private L2RiskAttackFeatureVO convertToFeatureVO(RiskAttackFeatureDO feature,
                                                     RiskDetailsDO detail,
                                                     Map<Long, RiskCategoryDO> categoryMap) {
        // 攻击特征 VO 同时暴露综合同步状态和单路索引状态，方便定位是 ES 还是 Milvus 未完成。
        RiskCategoryDO category = categoryMap.get(feature.getCategoryId());
        return L2RiskAttackFeatureVO.builder()
                .id(feature.getId())
                .riskDetailsId(feature.getRiskDetailsId())
                .detailsName(detail == null ? null : detail.getDetailsName())
                .categoryId(feature.getCategoryId())
                .categoryName(category == null ? null : category.getCategoryName())
                .featureCode(feature.getFeatureCode())
                .featureText(feature.getFeatureText())
                .normalizedText(feature.getNormalizedText())
                .featureType(feature.getFeatureType())
                .polarity(feature.getPolarity())
                .riskLevel(feature.getRiskLevel())
                .language(feature.getLanguage())
                .tags(feature.getTags())
                .source(feature.getSource())
                .weight(feature.getWeight())
                .contentHash(feature.getContentHash())
                .version(feature.getVersion())
                .syncStatus(feature.getSyncStatus())
                .syncStatusDesc(syncStatusDesc(feature.getSyncStatus()))
                .esSyncStatus(feature.getEsSyncStatus())
                .milvusSyncStatus(feature.getMilvusSyncStatus())
                .status(feature.getStatus())
                .creator(feature.getCreator())
                .updater(feature.getUpdater())
                .createTime(feature.getCreateTime())
                .updateTime(feature.getUpdateTime())
                .build();
    }

    private L2KbSyncEventVO convertToSyncEventVO(KbSyncEventDO event) {
        // 同步事件 VO 保留 payload 快照，便于后台直接查看当时要同步的完整数据。
        return L2KbSyncEventVO.builder()
                .id(event.getId())
                .eventId(event.getEventId())
                .aggregateType(event.getAggregateType())
                .aggregateId(event.getAggregateId())
                .operationType(event.getOperationType())
                .riskDetailsId(event.getRiskDetailsId())
                .contentHash(event.getContentHash())
                .version(event.getVersion())
                .payload(event.getPayload())
                .esStatus(event.getEsStatus())
                .esStatusDesc(syncStatusDesc(event.getEsStatus()))
                .milvusStatus(event.getMilvusStatus())
                .milvusStatusDesc(syncStatusDesc(event.getMilvusStatus()))
                .retryCount(event.getRetryCount())
                .nextRetryTime(event.getNextRetryTime())
                .lastError(event.getLastError())
                .createTime(event.getCreateTime())
                .updateTime(event.getUpdateTime())
                .build();
    }

    private String syncStatusDesc(Integer status) {
        if (status == null) {
            return null;
        }
        // 使用枚举描述统一前后端显示口径，避免前端硬编码状态文案。
        for (KbSyncStatusEnums statusEnum : KbSyncStatusEnums.values()) {
            if (statusEnum.getCode().equals(status)) {
                return statusEnum.getDesc();
            }
        }
        return "未知状态";
    }

    private String sha256(String value) {
        try {
            // SHA-256 用于生成稳定 contentHash：同样的风险小类、极性和归一化文本会得到同一个 hash。
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedHash = digest.digest(StringUtils.defaultString(value).getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : encodedHash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256 摘要算法", ex);
        }
    }
}
