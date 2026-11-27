package com.kant.llm.eval.knowledge.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kant.framework.convention.Result;
import com.kant.framework.web.Results;
import com.kant.llm.eval.knowledge.dao.entity.KnowledgeRiskWordDO;
import com.kant.llm.eval.knowledge.dto.req.CreateKnowledgeRiskKeywordRequest;
import com.kant.llm.eval.knowledge.dto.req.KnowledgeRiskKeywordPageRequest;
import com.kant.llm.eval.knowledge.dto.req.UpdateKnowledgeRiskKeywordRequest;
import com.kant.llm.eval.knowledge.dto.resp.KnowledgeRiskKeywordVO;
import com.kant.llm.eval.knowledge.service.KnowledgeRiskWordService;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 风险特征词库管理接口。
 */
@RestController
@RequestMapping("/knowledge/risk/keyword")
@RequiredArgsConstructor
public class KnowledgeRiskKeywordController {

    private static final int DEFAULT_CURRENT = 1;

    private static final int DEFAULT_SIZE = 10;

    private final KnowledgeRiskWordService knowledgeRiskWordService;

    /**
     * 新增风险词库特征词。
     *
     * @param request 创建请求
     * @return 是否创建成功
     */
    @PostMapping("/create")
    public Result<Boolean> createKeyword(@RequestBody CreateKnowledgeRiskKeywordRequest request) {
        KnowledgeRiskWordDO entity = KnowledgeRiskWordDO.builder().build();
        BeanUtils.copyProperties(request, entity);
        entity.setDeleted(false);
        return Results.success(knowledgeRiskWordService.save(entity));
    }

    /**
     * 更新风险词库特征词。
     *
     * @param request 更新请求
     * @return 是否更新成功
     */
    @PutMapping("/update")
    public Result<Boolean> updateKeyword(@RequestBody UpdateKnowledgeRiskKeywordRequest request) {
        KnowledgeRiskWordDO entity = KnowledgeRiskWordDO.builder().build();
        BeanUtils.copyProperties(request, entity);
        return Results.success(knowledgeRiskWordService.updateById(entity));
    }

    /**
     * 逻辑删除风险词库特征词。
     *
     * @param id 特征词 ID
     * @return 是否删除成功
     */
    @DeleteMapping("/{id}")
    public Result<Boolean> deleteKeyword(@PathVariable Long id) {
        return Results.success(knowledgeRiskWordService.removeById(id));
    }

    /**
     * 根据 ID 查询风险词库特征词。
     *
     * @param id 特征词 ID
     * @return 特征词详情
     */
    @GetMapping("/{id}")
    public Result<KnowledgeRiskKeywordVO> getKeyword(@PathVariable Long id) {
        return Results.success(convertToKeywordVO(knowledgeRiskWordService.getById(id)));
    }

    /**
     * 查询全部风险词库特征词。
     *
     * @return 特征词列表
     */
    @GetMapping("/list")
    public Result<List<KnowledgeRiskKeywordVO>> listKeywords() {
        LambdaQueryWrapper<KnowledgeRiskWordDO> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.orderByAsc(KnowledgeRiskWordDO::getRiskDetailsId)
                .orderByDesc(KnowledgeRiskWordDO::getUpdateTime)
                .orderByDesc(KnowledgeRiskWordDO::getId);
        return Results.success(knowledgeRiskWordService.list(queryWrapper).stream()
                .map(this::convertToKeywordVO)
                .collect(Collectors.toList()));
    }

    /**
     * 分页查询风险词库特征词。
     *
     * @param request 查询条件，支持按风险明细、关键词和匹配类型筛选
     * @return 特征词分页结果
     */
    @PostMapping("/page")
    public Result<Page<KnowledgeRiskKeywordVO>> pageKeywords(@RequestBody KnowledgeRiskKeywordPageRequest request) {
        KnowledgeRiskKeywordPageRequest queryRequest = request == null ? new KnowledgeRiskKeywordPageRequest() : request;
        LambdaQueryWrapper<KnowledgeRiskWordDO> queryWrapper = new LambdaQueryWrapper<>();
        if (queryRequest.getRiskDetailsId() != null) {
            queryWrapper.eq(KnowledgeRiskWordDO::getRiskDetailsId, queryRequest.getRiskDetailsId());
        }
        if (StringUtils.hasText(queryRequest.getKeyword())) {
            queryWrapper.like(KnowledgeRiskWordDO::getKeyword, queryRequest.getKeyword());
        }
        if (queryRequest.getMatchType() != null) {
            queryWrapper.eq(KnowledgeRiskWordDO::getMatchType, queryRequest.getMatchType());
        }
        queryWrapper.orderByAsc(KnowledgeRiskWordDO::getRiskDetailsId)
                .orderByDesc(KnowledgeRiskWordDO::getUpdateTime)
                .orderByDesc(KnowledgeRiskWordDO::getId);
        Page<KnowledgeRiskWordDO> pageResult = knowledgeRiskWordService.page(
                new Page<>(pageCurrent(queryRequest.getCurrent()), pageSize(queryRequest.getSize())),
                queryWrapper);
        Page<KnowledgeRiskKeywordVO> voPage = new Page<>(pageResult.getCurrent(), pageResult.getSize(), pageResult.getTotal());
        voPage.setRecords(pageResult.getRecords().stream()
                .map(this::convertToKeywordVO)
                .collect(Collectors.toList()));
        return Results.success(voPage);
    }

    private KnowledgeRiskKeywordVO convertToKeywordVO(KnowledgeRiskWordDO entity) {
        if (entity == null) {
            return null;
        }
        KnowledgeRiskKeywordVO vo = new KnowledgeRiskKeywordVO();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }

    private int pageCurrent(Integer current) {
        return current == null || current <= 0 ? DEFAULT_CURRENT : current;
    }

    private int pageSize(Integer size) {
        return size == null || size <= 0 ? DEFAULT_SIZE : size;
    }
}
