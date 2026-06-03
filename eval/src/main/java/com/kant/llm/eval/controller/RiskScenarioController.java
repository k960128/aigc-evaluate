package com.kant.llm.eval.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kant.llm.eval.common.web.Results;
import com.kant.llm.eval.common.convention.Result;
import com.kant.llm.eval.dao.entity.RiskScenarioDO;
import com.kant.llm.eval.service.RiskScenarioService;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

/**
 * 安全评测 - 风险场景与裁判规则 (父分片) 管理接口
 */
@RestController
@RequestMapping("/risk/scenarios")
@RequiredArgsConstructor
public class RiskScenarioController {

    private final RiskScenarioService riskScenarioService;

    /**
     * 分页查询场景列表 (支持按大类过滤和名称模糊搜索)
     */
    @GetMapping("/page")
    public Result<Page<RiskScenarioDO>> page(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) Long majorCategoryId,
            @RequestParam(required = false) String keyword) {

        LambdaQueryWrapper<RiskScenarioDO> queryWrapper = new LambdaQueryWrapper<>();
        if (majorCategoryId != null) {
            queryWrapper.eq(RiskScenarioDO::getMajorCategoryId, majorCategoryId);
        }
        if (StringUtils.hasText(keyword)) {
            queryWrapper.like(RiskScenarioDO::getScenarioName, keyword)
                    .or()
                    .like(RiskScenarioDO::getScenarioCode, keyword);
        }
        queryWrapper.orderByDesc(RiskScenarioDO::getCreateTime);

        Page<RiskScenarioDO> pageResult = riskScenarioService.page(new Page<>(current, size), queryWrapper);
        return Results.success(pageResult);
    }

    /**
     * 新增风险场景
     */
    @PostMapping
    public Result<Boolean> save(@RequestBody RiskScenarioDO riskScenarioDO) {
        return Results.success(riskScenarioService.save(riskScenarioDO));
    }

    /**
     * 修改风险场景 (主要用于修改 judge_rule 裁判红线)
     */
    @PutMapping
    public Result<Boolean> update(@RequestBody RiskScenarioDO riskScenarioDO) {
        return Results.success(riskScenarioService.updateById(riskScenarioDO));
    }

    /**
     * 删除风险场景
     */
    @DeleteMapping("/{id}")
    public Result<Boolean> delete(@PathVariable Long id) {
        return Results.success(riskScenarioService.removeById(id));
    }
}
