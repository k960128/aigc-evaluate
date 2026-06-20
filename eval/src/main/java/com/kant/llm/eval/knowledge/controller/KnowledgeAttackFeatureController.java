package com.kant.llm.eval.knowledge.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kant.framework.convention.Result;
import com.kant.llm.common.enums.KbSyncStatusEnums;
import com.kant.framework.web.Results;
import com.kant.llm.eval.knowledge.dao.entity.KnowledgeSyncEventDO;
import com.kant.llm.eval.knowledge.dao.entity.KnowledgeAttackFeatureDO;
import com.kant.llm.eval.risk.dao.entity.RiskDetailRuleDO;
import com.kant.llm.eval.knowledge.dto.req.CreateL2KnowledgeFeatureRequest;
import com.kant.llm.eval.knowledge.dto.req.CreateL2KnowledgeRuleRequest;
import com.kant.llm.eval.knowledge.dto.req.L2KbSyncEventPageRequest;
import com.kant.llm.eval.knowledge.dto.req.L2KnowledgeFeaturePageRequest;
import com.kant.llm.eval.knowledge.dto.req.L2KnowledgeRulePageRequest;
import com.kant.llm.eval.knowledge.dto.req.UpdateL2KnowledgeFeatureRequest;
import com.kant.llm.eval.knowledge.dto.req.UpdateL2KnowledgeRuleRequest;
import com.kant.llm.eval.knowledge.dto.resp.KbSyncEventVO;
import com.kant.llm.eval.knowledge.dto.resp.L2KnowledgeSyncSubmitVO;
import com.kant.llm.eval.knowledge.dto.resp.RiskAttackFeatureVO;
import com.kant.llm.eval.risk.dto.resp.RiskDetailRuleVO;
import com.kant.llm.eval.knowledge.service.KnowledgeSyncEventService;
import com.kant.llm.eval.knowledge.service.L2KnowledgeSyncService;
import com.kant.llm.eval.knowledge.service.KnowledgeAttackFeatureService;
import com.kant.llm.eval.risk.service.RiskDetailRuleService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * L2 知识库管理接口。
 *
 * <p>该 Controller 先定义知识库维护的 HTTP 边界：风险小类规则、语义特征、同步事件查询。
 * MySQL 仍是知识库事实源；ES/PGVector 同步事件写入和真实索引同步后续应下沉到 Service 层统一处理。</p>
 */
@RestController
@RequestMapping("/Knowledge/attack-feature")
@RequiredArgsConstructor
public class KnowledgeAttackFeatureController {

    private static final int DEFAULT_CURRENT = 1;

    private static final int DEFAULT_SIZE = 10;

    private static final int ENABLED_STATUS = 1;

    private static final int DISABLED_STATUS = 0;

    private static final int DEFAULT_VERSION = 1;

    private static final BigDecimal DEFAULT_WEIGHT = BigDecimal.ONE;

    private final RiskDetailRuleService riskDetailRuleService;

    private final KnowledgeAttackFeatureService knowledgeAttackFeatureService;

    private final KnowledgeSyncEventService knowledgeSyncEventService;

    private final L2KnowledgeSyncService l2KnowledgeSyncService;

    /**
     * 分页查询 L2 风险小类判定规则。
     *
     * @param request 查询条件，支持按风险小类、严重等级、状态和规则关键词筛选
     * @return 风险小类判定规则分页结果
     */
    @PostMapping("/rules/page")
    public Result<Page<RiskDetailRuleVO>> pageRules(@RequestBody L2KnowledgeRulePageRequest request) {
        L2KnowledgeRulePageRequest queryRequest = request == null ? new L2KnowledgeRulePageRequest() : request;
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
        if (StringUtils.hasText(queryRequest.getKeyword())) {
            queryWrapper.like(RiskDetailRuleDO::getJudgeRule, queryRequest.getKeyword());
        }
        queryWrapper.orderByAsc(RiskDetailRuleDO::getRiskDetailsId)
                .orderByDesc(RiskDetailRuleDO::getUpdateTime);
        Page<RiskDetailRuleDO> pageResult = riskDetailRuleService.page(
                new Page<>(pageCurrent(queryRequest.getCurrent()), pageSize(queryRequest.getSize())),
                queryWrapper);
        Page<RiskDetailRuleVO> voPage = new Page<>(pageResult.getCurrent(), pageResult.getSize(), pageResult.getTotal());
        voPage.setRecords(pageResult.getRecords().stream()
                .map(this::convertToRuleVO)
                .collect(Collectors.toList()));
        return Results.success(voPage);
    }

    /**
     * 根据 ID 查询 L2 风险小类判定规则。
     *
     * @param id 规则 ID
     * @return 风险小类判定规则详情
     */
    @GetMapping("/rules/{id}")
    public Result<RiskDetailRuleVO> getRule(@PathVariable Long id) {
        return Results.success(convertToRuleVO(riskDetailRuleService.getById(id)));
    }

    /**
     * 新增 L2 风险小类判定规则。
     *
     * @param request 创建请求
     * @return 是否创建成功
     */
    @PostMapping("/rules")
    public Result<Boolean> createRule(@RequestBody CreateL2KnowledgeRuleRequest request) {
        RiskDetailRuleDO entity = new RiskDetailRuleDO();
        BeanUtils.copyProperties(request, entity);
        entity.setVersion(DEFAULT_VERSION);
        entity.setStatus(ENABLED_STATUS);
        return Results.success(riskDetailRuleService.save(entity));
    }

    /**
     * 更新 L2 风险小类判定规则。
     *
     * @param request 更新请求
     * @return 是否更新成功
     */
    @PutMapping("/rules")
    public Result<Boolean> updateRule(@RequestBody UpdateL2KnowledgeRuleRequest request) {
        RiskDetailRuleDO entity = new RiskDetailRuleDO();
        BeanUtils.copyProperties(request, entity);
        return Results.success(riskDetailRuleService.updateById(entity));
    }

    /**
     * 启用或禁用 L2 风险小类判定规则。
     *
     * @param id 规则 ID
     * @param enabled true-启用，false-禁用
     * @return 是否更新成功
     */
    @PutMapping("/rules/{id}/status")
    public Result<Boolean> updateRuleStatus(@PathVariable Long id, @RequestParam("enabled") Boolean enabled) {
        RiskDetailRuleDO entity = new RiskDetailRuleDO();
        entity.setId(id);
        entity.setStatus(Boolean.FALSE.equals(enabled) ? DISABLED_STATUS : ENABLED_STATUS);
        return Results.success(riskDetailRuleService.updateById(entity));
    }

    /**
     * 逻辑删除 L2 风险小类判定规则。
     *
     * @param id 规则 ID
     * @return 是否删除成功
     */
    @DeleteMapping("/rules/{id}")
    public Result<Boolean> deleteRule(@PathVariable Long id) {
        return Results.success(riskDetailRuleService.removeById(id));
    }

    /**
     * 分页查询 L2 攻击特征知识。
     *
     * @param request 查询条件，支持按大类、小类、特征类型、极性、同步状态和文本关键词筛选
     * @return 攻击特征分页结果
     */
    @PostMapping("/features/page")
    public Result<Page<RiskAttackFeatureVO>> pageFeatures(@RequestBody L2KnowledgeFeaturePageRequest request) {
        L2KnowledgeFeaturePageRequest queryRequest = request == null ? new L2KnowledgeFeaturePageRequest() : request;
        LambdaQueryWrapper<KnowledgeAttackFeatureDO> queryWrapper = new LambdaQueryWrapper<>();
        if (queryRequest.getCategoryId() != null) {
            queryWrapper.eq(KnowledgeAttackFeatureDO::getCategoryId, queryRequest.getCategoryId());
        }
        if (queryRequest.getRiskDetailsId() != null) {
            queryWrapper.eq(KnowledgeAttackFeatureDO::getRiskDetailsId, queryRequest.getRiskDetailsId());
        }
        if (StringUtils.hasText(queryRequest.getFeatureType())) {
            queryWrapper.eq(KnowledgeAttackFeatureDO::getFeatureType, queryRequest.getFeatureType());
        }
        if (StringUtils.hasText(queryRequest.getPolarity())) {
            queryWrapper.eq(KnowledgeAttackFeatureDO::getPolarity, queryRequest.getPolarity());
        }
        if (queryRequest.getRiskLevel() != null) {
            queryWrapper.eq(KnowledgeAttackFeatureDO::getRiskLevel, queryRequest.getRiskLevel());
        }
        if (queryRequest.getSyncStatus() != null) {
            queryWrapper.eq(KnowledgeAttackFeatureDO::getSyncStatus, queryRequest.getSyncStatus());
        }
        if (queryRequest.getEsSyncStatus() != null) {
            queryWrapper.eq(KnowledgeAttackFeatureDO::getEsSyncStatus, queryRequest.getEsSyncStatus());
        }
        if (queryRequest.getPgSyncStatus() != null) {
            queryWrapper.eq(KnowledgeAttackFeatureDO::getPgSyncStatus, queryRequest.getPgSyncStatus());
        }
        if (queryRequest.getStatus() != null) {
            queryWrapper.eq(KnowledgeAttackFeatureDO::getStatus, queryRequest.getStatus());
        }
        if (StringUtils.hasText(queryRequest.getKeyword())) {
            queryWrapper.and(wrapper -> wrapper
                    .like(KnowledgeAttackFeatureDO::getFeatureText, queryRequest.getKeyword())
                    .or()
                    .like(KnowledgeAttackFeatureDO::getNormalizedText, queryRequest.getKeyword()));
        }
        queryWrapper.orderByAsc(KnowledgeAttackFeatureDO::getRiskDetailsId)
                .orderByDesc(KnowledgeAttackFeatureDO::getUpdateTime);
        Page<KnowledgeAttackFeatureDO> pageResult = knowledgeAttackFeatureService.page(
                new Page<>(pageCurrent(queryRequest.getCurrent()), pageSize(queryRequest.getSize())),
                queryWrapper);
        Page<RiskAttackFeatureVO> voPage = new Page<>(pageResult.getCurrent(), pageResult.getSize(), pageResult.getTotal());
        voPage.setRecords(pageResult.getRecords().stream()
                .map(this::convertToFeatureVO)
                .collect(Collectors.toList()));
        return Results.success(voPage);
    }

    /**
     * 根据 ID 查询 L2 攻击特征知识。
     *
     * @param id 特征 ID
     * @return 攻击特征详情
     */
    @GetMapping("/features/{id}")
    public Result<RiskAttackFeatureVO> getFeature(@PathVariable Long id) {
        return Results.success(convertToFeatureVO(knowledgeAttackFeatureService.getById(id)));
    }

    /**
     * 新增 L2 攻击特征知识。
     *
     * @param request 创建请求
     * @return 是否创建成功
     */
    @PostMapping("/features/create")
    public Result<Boolean> createFeature(@RequestBody CreateL2KnowledgeFeatureRequest request) {
        KnowledgeAttackFeatureDO entity = new KnowledgeAttackFeatureDO();
        BeanUtils.copyProperties(request, entity);
        fillFeatureTextDefaults(entity);
        entity.setVersion(DEFAULT_VERSION);
        entity.setStatus(ENABLED_STATUS);
        entity.setSyncStatus(KbSyncStatusEnums.PENDING.getCode());
        entity.setEsSyncStatus(KbSyncStatusEnums.PENDING.getCode());
        entity.setMilvusSyncStatus(KbSyncStatusEnums.PENDING.getCode());
        entity.setPgSyncStatus(KbSyncStatusEnums.PENDING.getCode());
        if (entity.getWeight() == null) {
            entity.setWeight(DEFAULT_WEIGHT);
        }
        return Results.success(knowledgeAttackFeatureService.save(entity));
    }

    /**
     * 更新 L2 攻击特征知识。
     *
     * <p>特征内容变更后，默认把同步状态重置为待同步，等待后续同步事件服务处理。</p>
     *
     * @param request 更新请求
     * @return 是否更新成功
     */
    @PutMapping("/features/update")
    public Result<Boolean> updateFeature(@RequestBody UpdateL2KnowledgeFeatureRequest request) {
        KnowledgeAttackFeatureDO entity = new KnowledgeAttackFeatureDO();
        BeanUtils.copyProperties(request, entity);
        fillFeatureTextDefaults(entity);
        entity.setSyncStatus(KbSyncStatusEnums.PENDING.getCode());
        entity.setEsSyncStatus(KbSyncStatusEnums.PENDING.getCode());
        entity.setMilvusSyncStatus(KbSyncStatusEnums.PENDING.getCode());
        entity.setPgSyncStatus(KbSyncStatusEnums.PENDING.getCode());
        return Results.success(knowledgeAttackFeatureService.updateById(entity));
    }

    /**
     * 启用或禁用 L2 攻击特征知识。
     *
     * @param id 特征 ID
     * @param enabled true-启用，false-禁用
     * @return 是否更新成功
     */
    @PutMapping("/features/{id}/status")
    public Result<Boolean> updateFeatureStatus(@PathVariable Long id, @RequestParam("enabled") Boolean enabled) {
        KnowledgeAttackFeatureDO entity = new KnowledgeAttackFeatureDO();
        entity.setId(id);
        entity.setStatus(Boolean.FALSE.equals(enabled) ? DISABLED_STATUS : ENABLED_STATUS);
        entity.setSyncStatus(KbSyncStatusEnums.PENDING.getCode());
        entity.setEsSyncStatus(KbSyncStatusEnums.PENDING.getCode());
        entity.setMilvusSyncStatus(KbSyncStatusEnums.PENDING.getCode());
        entity.setPgSyncStatus(KbSyncStatusEnums.PENDING.getCode());
        return Results.success(knowledgeAttackFeatureService.updateById(entity));
    }

    /**
     * 逻辑删除 L2 攻击特征知识。
     *
     * @param id 特征 ID
     * @return 是否删除成功
     */
    @DeleteMapping("/features/{id}")
    public Result<Boolean> deleteFeature(@PathVariable Long id) {
        return Results.success(knowledgeAttackFeatureService.removeById(id));
    }

    /**
     * 分页查询知识库同步事件。
     *
     * @param request 查询条件，支持按事件、聚合对象、操作类型和 ES/PG 状态筛选
     * @return 同步事件分页结果
     */
    @PostMapping("/sync-events/page")
    public Result<Page<KbSyncEventVO>> pageSyncEvents(@RequestBody L2KbSyncEventPageRequest request) {
        L2KbSyncEventPageRequest queryRequest = request == null ? new L2KbSyncEventPageRequest() : request;
        LambdaQueryWrapper<KnowledgeSyncEventDO> queryWrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(queryRequest.getEventId())) {
            queryWrapper.eq(KnowledgeSyncEventDO::getEventId, queryRequest.getEventId());
        }
        if (StringUtils.hasText(queryRequest.getAggregateType())) {
            queryWrapper.eq(KnowledgeSyncEventDO::getAggregateType, queryRequest.getAggregateType());
        }
        if (queryRequest.getAggregateId() != null) {
            queryWrapper.eq(KnowledgeSyncEventDO::getAggregateId, queryRequest.getAggregateId());
        }
        if (StringUtils.hasText(queryRequest.getOperationType())) {
            queryWrapper.eq(KnowledgeSyncEventDO::getOperationType, queryRequest.getOperationType());
        }
        if (queryRequest.getRiskDetailsId() != null) {
            queryWrapper.eq(KnowledgeSyncEventDO::getRiskDetailsId, queryRequest.getRiskDetailsId());
        }
        if (queryRequest.getEsStatus() != null) {
            queryWrapper.eq(KnowledgeSyncEventDO::getEsStatus, queryRequest.getEsStatus());
        }
        if (queryRequest.getPgStatus() != null) {
            queryWrapper.eq(KnowledgeSyncEventDO::getPgStatus, queryRequest.getPgStatus());
        }
        queryWrapper.orderByDesc(KnowledgeSyncEventDO::getCreateTime);
        Page<KnowledgeSyncEventDO> pageResult = knowledgeSyncEventService.page(
                new Page<>(pageCurrent(queryRequest.getCurrent()), pageSize(queryRequest.getSize())),
                queryWrapper);
        Page<KbSyncEventVO> voPage = new Page<>(pageResult.getCurrent(), pageResult.getSize(), pageResult.getTotal());
        voPage.setRecords(pageResult.getRecords().stream()
                .map(this::convertToSyncEventVO)
                .collect(Collectors.toList()));
        return Results.success(voPage);
    }

    /**
     * 根据 ID 查询知识库同步事件。
     *
     * @param id 同步事件 ID
     * @return 同步事件详情
     */
    @GetMapping("/sync-events/{id}")
    public Result<KbSyncEventVO> getSyncEvent(@PathVariable Long id) {
        return Results.success(convertToSyncEventVO(knowledgeSyncEventService.getById(id)));
    }

    /**
     * 一键提交 L2 知识库索引同步任务。
     *
     * <p>接口只负责把同步批次投递到 RocketMQ，真正的待同步知识扫描、事件拆分、ES/PG 写入都在消费者异步完成。</p>
     *
     * @return 同步任务提交结果
     */
    @PostMapping("/sync")
    public Result<L2KnowledgeSyncSubmitVO> syncPendingKnowledge() {
        return Results.success(l2KnowledgeSyncService.submitPendingSync());
    }

    private RiskDetailRuleVO convertToRuleVO(RiskDetailRuleDO entity) {
        if (entity == null) {
            return null;
        }
        RiskDetailRuleVO vo = new RiskDetailRuleVO();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }

    private RiskAttackFeatureVO convertToFeatureVO(KnowledgeAttackFeatureDO entity) {
        if (entity == null) {
            return null;
        }
        RiskAttackFeatureVO vo = new RiskAttackFeatureVO();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }

    private KbSyncEventVO convertToSyncEventVO(KnowledgeSyncEventDO entity) {
        if (entity == null) {
            return null;
        }
        KbSyncEventVO vo = new KbSyncEventVO();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }

    private int pageCurrent(Integer current) {
        return current == null || current <= 0 ? DEFAULT_CURRENT : current;
    }

    private int pageSize(Integer size) {
        return size == null || size <= 0 ? DEFAULT_SIZE : size;
    }

    /**
     * 补齐攻击特征文本相关默认值。
     *
     * <p>risk_attack_feature.content_hash 是数据库非空字段。Controller 当前只是接口定义层，
     * 但为了让新增接口能直接落库，这里先按 normalizedText/featureText 生成稳定 SHA-256。
     * 后续如果下沉到 Service，可把这段逻辑统一迁移到知识库领域服务。</p>
     */
    private void fillFeatureTextDefaults(KnowledgeAttackFeatureDO entity) {
        entity.setFeatureCode(UUID.randomUUID().toString());
        if (!StringUtils.hasText(entity.getNormalizedText()) && StringUtils.hasText(entity.getFeatureText())) {
            entity.setNormalizedText(entity.getFeatureText());
        }
        if (!StringUtils.hasText(entity.getContentHash())) {
            String hashSource = StringUtils.hasText(entity.getNormalizedText())
                    ? entity.getNormalizedText()
                    : entity.getFeatureText();
            if (StringUtils.hasText(hashSource)) {
                entity.setContentHash(sha256(hashSource.trim().toLowerCase()));
            }
        }
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedHash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
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
