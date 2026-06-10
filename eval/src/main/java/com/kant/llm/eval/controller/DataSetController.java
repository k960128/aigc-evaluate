package com.kant.llm.eval.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kant.llm.eval.common.convention.Result;
import com.kant.llm.eval.common.web.Results;
import com.kant.llm.eval.dao.entity.DataSetDO;
import com.kant.llm.eval.dao.entity.DataSetSampleDO;
import com.kant.llm.eval.dto.req.CreateDataSetRequest;
import com.kant.llm.eval.dto.req.DataSetSamplePageRequest;
import com.kant.llm.eval.dto.req.UpdateDataSetRequest;
import com.kant.llm.eval.dto.resp.DataSetSampleVO;
import com.kant.llm.eval.dto.resp.DataSetVO;
import com.kant.llm.eval.service.DataSetSampleService;
import com.kant.llm.eval.service.DataSetService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
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
 * 数据集管理接口。
 *
 * <p>提供数据集的创建、更新、删除、详情、列表查询，以及根据数据集 ID 查询样本列表的能力。</p>
 */
@RestController
@RequestMapping("/data-set")
@RequiredArgsConstructor
public class DataSetController {

    private final DataSetService dataSetService;
    private final DataSetSampleService dataSetSampleService;

    /**
     * 创建数据集。
     *
     * @param request 创建数据集请求参数，包含数据集名称、类型、样本数量和描述信息
     * @return 创建成功后的数据集信息
     */
    @PostMapping("/create")
    public Result<DataSetVO> create(@RequestBody CreateDataSetRequest request) {
        DataSetDO entity = new DataSetDO();
        BeanUtils.copyProperties(request, entity);
        dataSetService.save(entity);
        return Results.success(convertToVO(entity));
    }

    /**
     * 更新数据集。
     *
     * @param request 更新数据集请求参数，包含数据集 ID 以及需要更新的字段
     * @return 更新后的数据集信息；数据集不存在时返回空数据
     */
    @PutMapping("/update")
    public Result<DataSetVO> update(@RequestBody UpdateDataSetRequest request) {
        DataSetDO entity = dataSetService.getById(request.getId());
        if (entity == null) {
            return Results.success(null);
        }
        BeanUtils.copyProperties(request, entity);
        dataSetService.updateById(entity);
        return Results.success(convertToVO(entity));
    }

    /**
     * 删除数据集。
     *
     * @param id 数据集 ID
     * @return 无返回数据
     */
    @DeleteMapping("/delete")
    public Result<Void> delete(@RequestParam("id") Long id) {
        dataSetService.removeById(id);
        return Results.success();
    }

    /**
     * 根据 ID 查询数据集详情。
     *
     * @param id 数据集 ID
     * @return 数据集详情；数据集不存在时返回空数据
     */
    @GetMapping("/get")
    public Result<DataSetVO> getById(@RequestParam("id") Long id) {
        DataSetDO entity = dataSetService.getById(id);
        if (entity == null) {
            return Results.success(null);
        }
        return Results.success(convertToVO(entity));
    }

    /**
     * 查询全部数据集列表。
     *
     * @return 数据集列表
     */
    @GetMapping("/list")
    public Result<List<DataSetVO>> list() {
        List<DataSetVO> voList = dataSetService.list().stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
        return Results.success(voList);
    }

    /**
     * 分页查询数据集样本列表。
     *
     * @param request 数据集样本分页查询请求参数，包含当前页码、每页数量和数据集 ID
     * @return 数据集样本分页结果
     */
    @PostMapping("/sample/page")
    public Result<Page<DataSetSampleVO>> pageSamples(@RequestBody DataSetSamplePageRequest request) {
        int current = request.getCurrent() == null ? 1 : request.getCurrent();
        int size = request.getSize() == null ? 10 : request.getSize();

        LambdaQueryWrapper<DataSetSampleDO> queryWrapper = new LambdaQueryWrapper<>();
        if (request.getDatasetId() != null) {
            queryWrapper.eq(DataSetSampleDO::getDatasetId, request.getDatasetId());
        }
        queryWrapper.orderByDesc(DataSetSampleDO::getCreateTime);

        Page<DataSetSampleDO> pageResult = dataSetSampleService.page(new Page<>(current, size), queryWrapper);
        Page<DataSetSampleVO> voPage = new Page<>(pageResult.getCurrent(), pageResult.getSize(), pageResult.getTotal());
        voPage.setRecords(pageResult.getRecords().stream()
                .map(this::convertToSampleVO)
                .collect(Collectors.toList()));
        return Results.success(voPage);
    }

    /**
     * 根据数据集 ID 查询样本列表。
     *
     * @param datasetId 数据集 ID
     * @return 指定数据集下的样本列表
     */
    @GetMapping("/sample/list")
    public Result<List<DataSetSampleVO>> listSamples(@RequestParam("datasetId") Long datasetId) {
        List<DataSetSampleVO> voList = dataSetSampleService.list(new LambdaQueryWrapper<DataSetSampleDO>()
                        .eq(DataSetSampleDO::getDatasetId, datasetId)
                        .orderByDesc(DataSetSampleDO::getCreateTime))
                .stream()
                .map(this::convertToSampleVO)
                .collect(Collectors.toList());
        return Results.success(voList);
    }

    /**
     * 将数据集实体转换为接口响应 VO。
     *
     * @param entity 数据集实体
     * @return 数据集响应对象
     */
    private DataSetVO convertToVO(DataSetDO entity) {
        return DataSetVO.builder()
                .id(entity.getId())
                .datasetName(entity.getDatasetName())
                .datasetType(entity.getDatasetType())
                .sampleCount(entity.getSampleCount())
                .description(entity.getDescription())
                .createTime(entity.getCreateTime())
                .updateTime(entity.getUpdateTime())
                .build();
    }

    /**
     * 将数据集样本实体转换为接口响应 VO。
     *
     * @param entity 数据集样本实体
     * @return 数据集样本响应对象
     */
    private DataSetSampleVO convertToSampleVO(DataSetSampleDO entity) {
        return DataSetSampleVO.builder()
                .id(entity.getId())
                .datasetId(entity.getDatasetId())
                .question(entity.getQuestion())
                .createTime(entity.getCreateTime())
                .updateTime(entity.getUpdateTime())
                .build();
    }
}
