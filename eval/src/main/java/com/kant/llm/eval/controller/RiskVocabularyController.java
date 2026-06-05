package com.kant.llm.eval.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kant.llm.eval.common.convention.Result;
import com.kant.llm.eval.common.web.Results;
import com.kant.llm.eval.dao.entity.RiskVocabularyKeywordDO;
import com.kant.llm.eval.dto.req.CreateRiskVocabularyKeywordRequest;
import com.kant.llm.eval.dto.req.RiskVocabularyKeywordPageRequest;
import com.kant.llm.eval.dto.req.UpdateRiskVocabularyKeywordRequest;
import com.kant.llm.eval.dto.resp.RiskVocabularyKeywordVO;
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
 * 安全评测 - 风险词库管理接口。
 *
 * <p>提供风险词库分组、特征词、分页查询以及特征词同步状态管理能力。</p>
 */
@RestController
@RequestMapping("/risk/vocabularies")
@RequiredArgsConstructor
public class RiskVocabularyController {
    private final RiskVocabularyKeywordService keywordService;

    /**
     * 创建风险词库特征词。
     *
     * <p>当请求未传入同步状态时，默认设置为 0，表示待同步。</p>
     *
     * @param request 特征词创建请求，包含所属分组、风险明细、特征词内容、风险等级和匹配模式等信息
     * @return 创建成功后的特征词信息
     */
    @PostMapping("/keyword/create")
    public Result<RiskVocabularyKeywordVO> createKeyword(@RequestBody CreateRiskVocabularyKeywordRequest request) {
        RiskVocabularyKeywordDO entity = new RiskVocabularyKeywordDO();
        BeanUtils.copyProperties(request, entity);
        entity.setSyncStatus(false);
        keywordService.save(entity);
        return Results.success(convertToKeywordVO(entity));
    }

    /**
     * 更新风险词库特征词。
     *
     * @param request 特征词更新请求，包含特征词 ID 以及需要更新的字段
     * @return 更新后的特征词信息；特征词不存在时返回空数据
     */
    @PutMapping("/keyword/update")
    public Result<RiskVocabularyKeywordVO> updateKeyword(@RequestBody UpdateRiskVocabularyKeywordRequest request) {
        RiskVocabularyKeywordDO entity = keywordService.getById(request.getId());
        if (entity == null) {
            return Results.success(null);
        }
        BeanUtils.copyProperties(request, entity);
        keywordService.updateById(entity);
        return Results.success(convertToKeywordVO(entity));
    }

    /**
     * 删除风险词库特征词。
     *
     * @param id 特征词 ID
     * @return 无返回数据
     */
    @DeleteMapping("/keyword/delete")
    public Result<Void> deleteKeyword(@RequestParam("id") Long id) {
        keywordService.removeById(id);
        return Results.success();
    }

    /**
     * 根据 ID 查询风险词库特征词详情。
     *
     * @param id 特征词 ID
     * @return 特征词详情；特征词不存在时返回空数据
     */
    @GetMapping("/keyword/get")
    public Result<RiskVocabularyKeywordVO> getKeywordById(@RequestParam("id") Long id) {
        RiskVocabularyKeywordDO entity = keywordService.getById(id);
        if (entity == null) {
            return Results.success(null);
        }
        return Results.success(convertToKeywordVO(entity));
    }

    /**
     * 查询全部风险词库特征词。
     *
     * @return 特征词列表
     */
    @GetMapping("/keyword/list")
    public Result<List<RiskVocabularyKeywordVO>> listKeyword() {
        List<RiskVocabularyKeywordVO> voList = keywordService.list().stream()
                .map(this::convertToKeywordVO)
                .collect(Collectors.toList());
        return Results.success(voList);
    }

    /**
     * 分页查询风险词库特征词。
     *
     * @param request 分页查询请求，支持按风险明细、分组、关键词、风险等级、匹配模式和同步状态筛选
     * @return 特征词分页结果
     */
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

    /**
     * 将待同步的风险词库特征词模拟同步到 Redis。
     *
     * <p>当前实现会查询同步状态为 0 的特征词，并批量更新为 1。</p>
     *
     * @return 同步处理结果描述
     */
    @PostMapping("/keyword/sync-to-redis")
    public Result<String> syncToRedis() {
        LambdaQueryWrapper<RiskVocabularyKeywordDO> query = new LambdaQueryWrapper<>();
        query.eq(RiskVocabularyKeywordDO::getSyncStatus, 0);
        List<RiskVocabularyKeywordDO> pendingList = keywordService.list(query);

        if (pendingList.isEmpty()) {
            return Results.success("当前没有需要同步的词条");
        }

        for (RiskVocabularyKeywordDO kw : pendingList) {
            kw.setSyncStatus(true);
        }
        keywordService.updateBatchById(pendingList);

        return Results.success("成功推送 " + pendingList.size() + " 条特征词，AC自动机将在一分钟内完成热更");
    }

    /**
     * 将风险词库特征词实体转换为接口响应 VO。
     *
     * @param entity 风险词库特征词实体
     * @return 风险词库特征词响应对象
     */
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
