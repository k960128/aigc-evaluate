package com.kant.llm.eval.risk.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kant.framework.convention.Result;
import com.kant.framework.web.Results;
import com.kant.llm.eval.risk.dao.entity.RiskCategoryDO;
import com.kant.llm.eval.risk.dao.entity.RiskDetailsDO;
import com.kant.llm.eval.risk.dto.resp.RiskCategoryTreeVO;
import com.kant.llm.eval.risk.dto.resp.RiskCategoryVO;
import com.kant.llm.eval.risk.dto.resp.RiskDetailsVO;
import com.kant.llm.eval.risk.service.RiskCategoryService;
import com.kant.llm.eval.risk.service.RiskDetailsService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 安全评测 - 风险分类管理接口
 */
@RestController
@RequestMapping("/risk/category")
@RequiredArgsConstructor
public class RiskCategoryController {

    private final RiskCategoryService riskCategoryService;
    private final RiskDetailsService riskDetailsService;

    /**
     * 分页查询风险分类
     */
    @GetMapping("/page")
    public Result<Page<RiskCategoryVO>> page(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String categoryName,
            @RequestParam(required = false) Integer status) {

        LambdaQueryWrapper<RiskCategoryDO> queryWrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(categoryName)) {
            queryWrapper.like(RiskCategoryDO::getCategoryName, categoryName);
        }
        if (status != null) {
            queryWrapper.eq(RiskCategoryDO::getStatus, status);
        }
        queryWrapper.orderByAsc(RiskCategoryDO::getSortOrder);

        Page<RiskCategoryDO> pageResult = riskCategoryService.page(new Page<>(current, size), queryWrapper);
        Page<RiskCategoryVO> voPage = new Page<>(pageResult.getCurrent(), pageResult.getSize(), pageResult.getTotal());
        voPage.setRecords(pageResult.getRecords().stream()
                .map(this::convertToCategoryVO)
                .collect(Collectors.toList()));
        return Results.success(voPage);
    }

    /**
     * 查询所有风险分类（不分页）
     */
    @GetMapping("/list")
    public Result<List<RiskCategoryVO>> list(
            @RequestParam(required = false) Integer status) {

        LambdaQueryWrapper<RiskCategoryDO> queryWrapper = new LambdaQueryWrapper<>();
        if (status != null) {
            queryWrapper.eq(RiskCategoryDO::getStatus, status);
        }
        queryWrapper.orderByAsc(RiskCategoryDO::getSortOrder);

        List<RiskCategoryVO> voList = riskCategoryService.list(queryWrapper).stream()
                .map(this::convertToCategoryVO)
                .collect(Collectors.toList());
        return Results.success(voList);
    }

    /**
     * 查询风险大类并挂载所属风险小类（不分页）
     *
     * @param status 可选状态，同时筛选风险大类和风险小类
     * @return 风险分类树，无小类的大类返回空 children
     */
    @GetMapping("/tree")
    public Result<List<RiskCategoryTreeVO>> tree(
            @RequestParam(required = false) Integer status) {
        LambdaQueryWrapper<RiskCategoryDO> categoryQuery = new LambdaQueryWrapper<>();
        categoryQuery.eq(status != null, RiskCategoryDO::getStatus, status)
                .orderByAsc(RiskCategoryDO::getSortOrder)
                .orderByAsc(RiskCategoryDO::getId);
        List<RiskCategoryDO> categories = riskCategoryService.list(categoryQuery);
        if (categories.isEmpty()) {
            return Results.success(List.of());
        }

        List<Long> categoryIds = categories.stream()
                .map(RiskCategoryDO::getId)
                .toList();
        LambdaQueryWrapper<RiskDetailsDO> detailsQuery = new LambdaQueryWrapper<>();
        detailsQuery.in(RiskDetailsDO::getCategoryId, categoryIds)
                .eq(status != null, RiskDetailsDO::getStatus, status)
                .orderByAsc(RiskDetailsDO::getSortOrder)
                .orderByAsc(RiskDetailsDO::getId);
        Map<Long, List<RiskDetailsVO>> detailsByCategoryId = riskDetailsService.list(detailsQuery).stream()
                .map(this::convertToDetailsVO)
                .collect(Collectors.groupingBy(RiskDetailsVO::getCategoryId));

        List<RiskCategoryTreeVO> tree = categories.stream()
                .map(category -> {
                    RiskCategoryTreeVO node = new RiskCategoryTreeVO();
                    BeanUtils.copyProperties(category, node);
                    node.setChildren(detailsByCategoryId.getOrDefault(category.getId(), List.of()));
                    return node;
                })
                .toList();
        return Results.success(tree);
    }

    /**
     * 根据ID查询风险分类详情
     */
    @GetMapping("/{id}")
    public Result<RiskCategoryVO> getById(@PathVariable Long id) {
        return Results.success(convertToCategoryVO(riskCategoryService.getById(id)));
    }

    /**
     * 新增风险分类
     */
    @PostMapping
    public Result<Boolean> save(@RequestBody RiskCategoryDO riskCategoryDO) {
        return Results.success(riskCategoryService.save(riskCategoryDO));
    }

    /**
     * 更新风险分类
     */
    @PutMapping
    public Result<Boolean> update(@RequestBody RiskCategoryDO riskCategoryDO) {
        return Results.success(riskCategoryService.updateById(riskCategoryDO));
    }

    /**
     * 删除风险分类
     */
    @DeleteMapping("/{id}")
    public Result<Boolean> delete(@PathVariable Long id) {
        return Results.success(riskCategoryService.removeById(id));
    }

    // ==================== RiskDetails (风险明细) 相关接口 ====================

    /**
     * 分页查询风险明细
     */
    @GetMapping("/details/page")
    public Result<Page<RiskDetailsVO>> detailsPage(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String detailsName,
            @RequestParam(required = false) Integer status) {

        LambdaQueryWrapper<RiskDetailsDO> queryWrapper = new LambdaQueryWrapper<>();
        if (categoryId != null) {
            queryWrapper.eq(RiskDetailsDO::getCategoryId, categoryId);
        }
        if (StringUtils.hasText(detailsName)) {
            queryWrapper.like(RiskDetailsDO::getDetailsName, detailsName);
        }
        if (status != null) {
            queryWrapper.eq(RiskDetailsDO::getStatus, status);
        }
        queryWrapper.orderByAsc(RiskDetailsDO::getSortOrder);

        Page<RiskDetailsDO> pageResult = riskDetailsService.page(new Page<>(current, size), queryWrapper);
        Page<RiskDetailsVO> voPage = new Page<>(pageResult.getCurrent(), pageResult.getSize(), pageResult.getTotal());
        voPage.setRecords(pageResult.getRecords().stream()
                .map(this::convertToDetailsVO)
                .collect(Collectors.toList()));
        return Results.success(voPage);
    }

    /**
     * 根据分类ID查询风险明细列表（不分页）
     */
    @GetMapping("/details/list")
    public Result<List<RiskDetailsVO>> detailsList() {
        return Results.success(riskDetailsService.detailsList());
    }

    /**
     * 根据ID查询风险明细详情
     */
    @GetMapping("/details/{id}")
    public Result<RiskDetailsVO> getDetailsById(@PathVariable Long id) {
        return Results.success(convertToDetailsVO(riskDetailsService.getById(id)));
    }

    /**
     * 新增风险明细
     */
    @PostMapping("/details")
    public Result<Boolean> saveDetails(@RequestBody RiskDetailsDO riskDetailsDO) {
        return Results.success(riskDetailsService.save(riskDetailsDO));
    }

    /**
     * 更新风险明细
     */
    @PutMapping("/details")
    public Result<Boolean> updateDetails(@RequestBody RiskDetailsDO riskDetailsDO) {
        return Results.success(riskDetailsService.updateById(riskDetailsDO));
    }

    /**
     * 删除风险明细
     */
    @DeleteMapping("/details/{id}")
    public Result<Boolean> deleteDetails(@PathVariable Long id) {
        return Results.success(riskDetailsService.removeById(id));
    }

    private RiskCategoryVO convertToCategoryVO(RiskCategoryDO entity) {
        if (entity == null) {
            return null;
        }
        RiskCategoryVO vo = new RiskCategoryVO();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }

    private RiskDetailsVO convertToDetailsVO(RiskDetailsDO entity) {
        if (entity == null) {
            return null;
        }
        RiskDetailsVO vo = new RiskDetailsVO();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }
}
