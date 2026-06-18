package com.kant.llm.eval.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kant.framework.convention.Result;
import com.kant.framework.web.Results;
import com.kant.llm.eval.dao.entity.RiskVocabularyKeywordDO;
import com.kant.llm.eval.dto.req.CreateRiskVocabularyKeywordRequest;
import com.kant.llm.eval.dto.req.RiskVocabularyKeywordPageRequest;
import com.kant.llm.eval.dto.req.UpdateRiskVocabularyKeywordRequest;
import com.kant.llm.eval.dto.resp.RiskVocabularyKeywordVO;
import com.kant.llm.eval.service.AcSnapshotDirtyShardService;
import com.kant.llm.eval.service.RiskVocabularyKeywordService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 风险词库管理接口。
 */
@RestController
@RequestMapping("/risk/vocabularies")
@RequiredArgsConstructor
public class RiskVocabularyController {

    private static final String SYNC_STATUS = "syncStatus";

    private final RiskVocabularyKeywordService keywordService;

    private final AcSnapshotDirtyShardService dirtyShardService;

    @PostMapping("/keyword/create")
    public Result<RiskVocabularyKeywordVO> createKeyword(@RequestBody CreateRiskVocabularyKeywordRequest request) {
        RiskVocabularyKeywordDO entity = new RiskVocabularyKeywordDO();
        BeanUtils.copyProperties(request, entity);
        entity.setSyncStatus(false);
        keywordService.save(entity);
        dirtyShardService.markDirty(entity.getRiskDetailsId(), "CREATE");
        return Results.success(convertToKeywordVO(entity));
    }

    @PutMapping("/keyword/update")
    public Result<RiskVocabularyKeywordVO> updateKeyword(@RequestBody UpdateRiskVocabularyKeywordRequest request) {
        RiskVocabularyKeywordDO entity = keywordService.getById(request.getId());
        if (entity == null) {
            return Results.success(null);
        }
        Long oldRiskDetailsId = entity.getRiskDetailsId();
        BeanUtils.copyProperties(request, entity, SYNC_STATUS);
        entity.setSyncStatus(false);
        keywordService.updateById(entity);
        dirtyShardService.markDirty(oldRiskDetailsId, "UPDATE_OLD");
        dirtyShardService.markDirty(entity.getRiskDetailsId(), "UPDATE_NEW");
        return Results.success(convertToKeywordVO(entity));
    }

    @DeleteMapping("/keyword/delete")
    public Result<Void> deleteKeyword(@RequestParam("id") Long id) {
        RiskVocabularyKeywordDO entity = keywordService.getById(id);
        keywordService.removeById(id);
        if (entity != null) {
            dirtyShardService.markDirty(entity.getRiskDetailsId(), "DELETE");
        }
        return Results.success();
    }

    @GetMapping("/keyword/get")
    public Result<RiskVocabularyKeywordVO> getKeywordById(@RequestParam("id") Long id) {
        RiskVocabularyKeywordDO entity = keywordService.getById(id);
        if (entity == null) {
            return Results.success(null);
        }
        return Results.success(convertToKeywordVO(entity));
    }

    @GetMapping("/keyword/list")
    public Result<List<RiskVocabularyKeywordVO>> listKeyword() {
        List<RiskVocabularyKeywordVO> voList = keywordService.list().stream()
                .map(this::convertToKeywordVO)
                .collect(Collectors.toList());
        return Results.success(voList);
    }

    @PostMapping("/keyword/page")
    public Result<Page<RiskVocabularyKeywordVO>> pageKeyword(@RequestBody RiskVocabularyKeywordPageRequest request) {
        int current = request.getCurrent() == null ? 1 : request.getCurrent();
        int size = request.getSize() == null ? 10 : request.getSize();

        LambdaQueryWrapper<RiskVocabularyKeywordDO> queryWrapper = new LambdaQueryWrapper<>();
        if (request.getRiskDetailsId() != null) {
            queryWrapper.eq(RiskVocabularyKeywordDO::getRiskDetailsId, request.getRiskDetailsId());
        }
        if (request.getGroupId() != null) {
            queryWrapper.eq(RiskVocabularyKeywordDO::getGroupId, request.getGroupId());
        }
        if (StringUtils.hasText(request.getKeyword())) {
            queryWrapper.like(RiskVocabularyKeywordDO::getKeyword, request.getKeyword());
        }
        if (request.getRiskLevel() != null) {
            queryWrapper.eq(RiskVocabularyKeywordDO::getRiskLevel, request.getRiskLevel());
        }
        if (request.getMatchType() != null) {
            queryWrapper.eq(RiskVocabularyKeywordDO::getMatchType, request.getMatchType());
        }
        if (request.getSyncStatus() != null) {
            queryWrapper.eq(RiskVocabularyKeywordDO::getSyncStatus, request.getSyncStatus());
        }
        queryWrapper.orderByDesc(RiskVocabularyKeywordDO::getCreateTime);

        Page<RiskVocabularyKeywordDO> pageResult = keywordService.page(new Page<>(current, size), queryWrapper);
        Page<RiskVocabularyKeywordVO> voPage = new Page<>(pageResult.getCurrent(), pageResult.getSize(), pageResult.getTotal());
        voPage.setRecords(pageResult.getRecords().stream()
                .map(this::convertToKeywordVO)
                .collect(Collectors.toList()));
        return Results.success(voPage);
    }

    @PostMapping("/keyword/version/publish")
    public Result<String> publishAcSnapshot() {
        return Results.success(keywordService.publishAcSnapshot());
    }

    private RiskVocabularyKeywordVO convertToKeywordVO(RiskVocabularyKeywordDO entity) {
        return RiskVocabularyKeywordVO.builder()
                .id(entity.getId())
                .riskDetailsId(entity.getRiskDetailsId())
                .keyword(entity.getKeyword())
                .riskLevel(entity.getRiskLevel())
                .matchType(entity.getMatchType())
                .syncStatus(entity.getSyncStatus())
                .creator(entity.getCreator())
                .updater(entity.getUpdater())
                .createTime(entity.getCreateTime())
                .updateTime(entity.getUpdateTime())
                .build();
    }
}
