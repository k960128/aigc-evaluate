package com.kant.llm.eval.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kant.llm.eval.common.convention.Result;
import com.kant.llm.eval.common.web.Results;
import com.kant.llm.eval.dao.entity.RiskScenarioDO;
import com.kant.llm.eval.dto.req.CreateRiskScenarioRequest;
import com.kant.llm.eval.dto.req.RiskScenarioPageRequest;
import com.kant.llm.eval.dto.req.UpdateRiskScenarioRequest;
import com.kant.llm.eval.dto.resp.RiskScenarioVO;
import com.kant.llm.eval.service.RiskScenarioService;
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
 * 安全评测 - 风险场景管理接口。
 *
 * <p>提供风险场景创建、更新、删除、详情、列表和分页查询能力。</p>
 */
@RestController
@RequestMapping("/risk/scenarios")
@RequiredArgsConstructor
public class RiskScenarioController {

    private final RiskScenarioService riskScenarioService;

    /**
     * 创建风险场景。
     *
     * @param request 风险场景创建请求，包含大类、编码、名称、裁判规则、严重等级和状态等信息
     * @return 创建成功后的风险场景信息
     */
    @PostMapping("/create")
    public Result<RiskScenarioVO> create(@RequestBody CreateRiskScenarioRequest request) {
        RiskScenarioDO entity = new RiskScenarioDO();
        BeanUtils.copyProperties(request, entity);
        riskScenarioService.save(entity);
        return Results.success(convertToVO(entity));
    }

    /**
     * 更新风险场景。
     *
     * @param request 风险场景更新请求，包含场景 ID 以及需要更新的字段
     * @return 更新后的风险场景信息；场景不存在时返回空数据
     */
    @PutMapping("/update")
    public Result<RiskScenarioVO> update(@RequestBody UpdateRiskScenarioRequest request) {
        RiskScenarioDO entity = riskScenarioService.getById(request.getId());
        if (entity == null) {
            return Results.success(null);
        }
        BeanUtils.copyProperties(request, entity);
        riskScenarioService.updateById(entity);
        return Results.success(convertToVO(entity));
    }

    /**
     * 删除风险场景。
     *
     * @param id 风险场景 ID
     * @return 无返回数据
     */
    @DeleteMapping("/delete")
    public Result<Void> delete(@RequestParam("id") Long id) {
        riskScenarioService.removeById(id);
        return Results.success();
    }

    /**
     * 根据 ID 查询风险场景详情。
     *
     * @param id 风险场景 ID
     * @return 风险场景详情；场景不存在时返回空数据
     */
    @GetMapping("/get")
    public Result<RiskScenarioVO> getById(@RequestParam("id") Long id) {
        RiskScenarioDO entity = riskScenarioService.getById(id);
        if (entity == null) {
            return Results.success(null);
        }
        return Results.success(convertToVO(entity));
    }

    /**
     * 查询全部风险场景。
     *
     * @return 风险场景列表
     */
    @GetMapping("/list")
    public Result<List<RiskScenarioVO>> list() {
        List<RiskScenarioVO> voList = riskScenarioService.list().stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
        return Results.success(voList);
    }

    /**
     * 分页查询风险场景。
     *
     * @param request 分页查询请求，支持按大类、关键词、严重等级和状态筛选
     * @return 风险场景分页结果
     */
    @PostMapping("/page")
    public Result<Page<RiskScenarioVO>> page(@RequestBody RiskScenarioPageRequest request) {
        int current = request.getCurrent() == null ? 1 : request.getCurrent();
        int size = request.getSize() == null ? 10 : request.getSize();

        LambdaQueryWrapper<RiskScenarioDO> queryWrapper = new LambdaQueryWrapper<>();
        if (request.getMajorCategoryId() != null) {
            queryWrapper.eq(RiskScenarioDO::getMajorCategoryId, request.getMajorCategoryId());
        }
        if (StringUtils.hasText(request.getKeyword())) {
            queryWrapper.and(wrapper -> wrapper
                    .like(RiskScenarioDO::getScenarioName, request.getKeyword())
                    .or()
                    .like(RiskScenarioDO::getScenarioCode, request.getKeyword()));
        }
        if (request.getSeverityLevel() != null) {
            queryWrapper.eq(RiskScenarioDO::getSeverityLevel, request.getSeverityLevel());
        }
        if (request.getStatus() != null) {
            queryWrapper.eq(RiskScenarioDO::getStatus, request.getStatus());
        }
        queryWrapper.orderByDesc(RiskScenarioDO::getCreateTime);

        Page<RiskScenarioDO> pageResult = riskScenarioService.page(new Page<>(current, size), queryWrapper);
        Page<RiskScenarioVO> voPage = new Page<>(pageResult.getCurrent(), pageResult.getSize(), pageResult.getTotal());
        voPage.setRecords(pageResult.getRecords().stream()
                .map(this::convertToVO)
                .collect(Collectors.toList()));
        return Results.success(voPage);
    }

    /**
     * 将风险场景实体转换为接口响应 VO。
     *
     * @param entity 风险场景实体
     * @return 风险场景响应对象
     */
    private RiskScenarioVO convertToVO(RiskScenarioDO entity) {
        return RiskScenarioVO.builder()
                .id(entity.getId())
                .majorCategoryId(entity.getMajorCategoryId())
                .scenarioCode(entity.getScenarioCode())
                .scenarioName(entity.getScenarioName())
                .judgeRule(entity.getJudgeRule())
                .severityLevel(entity.getSeverityLevel())
                .status(entity.getStatus())
                .creator(entity.getCreator())
                .updater(entity.getUpdater())
                .createTime(entity.getCreateTime())
                .updateTime(entity.getUpdateTime())
                .build();
    }
}
