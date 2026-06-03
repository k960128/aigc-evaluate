package com.kant.llm.eval.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kant.llm.eval.common.convention.Result;
import com.kant.llm.eval.common.web.Results;
import com.kant.llm.eval.dao.entity.RiskCategoryDO;
import com.kant.llm.eval.dao.entity.RiskDetailsDO;
import com.kant.llm.eval.service.RiskCategoryService;
import com.kant.llm.eval.service.RiskDetailsService;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
    public Result<Page<RiskCategoryDO>> page(
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

        return Results.success(riskCategoryService.page(new Page<>(current, size), queryWrapper));
    }

    /**
     * 查询所有风险分类（不分页）
     */
    @GetMapping("/list")
    public Result<List<RiskCategoryDO>> list(
            @RequestParam(required = false) Integer status) {

        LambdaQueryWrapper<RiskCategoryDO> queryWrapper = new LambdaQueryWrapper<>();
        if (status != null) {
            queryWrapper.eq(RiskCategoryDO::getStatus, status);
        }
        queryWrapper.orderByAsc(RiskCategoryDO::getSortOrder);

        return Results.success(riskCategoryService.list(queryWrapper));
    }

    /**
     * 根据ID查询风险分类详情
     */
    @GetMapping("/{id}")
    public Result<RiskCategoryDO> getById(@PathVariable Long id) {
        return Results.success(riskCategoryService.getById(id));
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
    public Result<Page<RiskDetailsDO>> detailsPage(
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

        return Results.success(riskDetailsService.page(new Page<>(current, size), queryWrapper));
    }

    /**
     * 根据分类ID查询风险明细列表（不分页）
     */
    @GetMapping("/details/list")
    public Result<List<RiskDetailsDO>> detailsList(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Integer status) {

        LambdaQueryWrapper<RiskDetailsDO> queryWrapper = new LambdaQueryWrapper<>();
        if (categoryId != null) {
            queryWrapper.eq(RiskDetailsDO::getCategoryId, categoryId);
        }
        if (status != null) {
            queryWrapper.eq(RiskDetailsDO::getStatus, status);
        }
        queryWrapper.orderByAsc(RiskDetailsDO::getSortOrder);

        return Results.success(riskDetailsService.list(queryWrapper));
    }

    /**
     * 根据ID查询风险明细详情
     */
    @GetMapping("/details/{id}")
    public Result<RiskDetailsDO> getDetailsById(@PathVariable Long id) {
        return Results.success(riskDetailsService.getById(id));
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
}