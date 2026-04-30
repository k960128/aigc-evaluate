package com.kant.llm.eval.controller;

import com.kant.llm.eval.common.convention.Result;
import com.kant.llm.eval.common.web.Results;
import com.kant.llm.eval.dao.entity.ModelManufacturerDO;
import com.kant.llm.eval.dto.req.CreateModelManufacturerRequest;
import com.kant.llm.eval.dto.req.UpdateModelManufacturerRequest;
import com.kant.llm.eval.dto.resp.ModelManufacturerVO;
import com.kant.llm.eval.service.ModelManufacturerService;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/source")
public class SourceController {

    private final ModelManufacturerService modelManufacturerService;

    public SourceController(ModelManufacturerService modelManufacturerService) {
        this.modelManufacturerService = modelManufacturerService;
    }

    /**
     * 创建模型厂商
     * @param request 创建请求参数，包含厂商名称、编码、默认URL等信息
     * @return 创建成功的厂商信息
     */
    @PostMapping("/manufacturer/create")
    public Result<ModelManufacturerVO> create(@RequestBody CreateModelManufacturerRequest request) {
        ModelManufacturerDO entity = ModelManufacturerDO.builder()
                .manufacturerName(request.getManufacturerName())
                .manufacturerCode(request.getManufacturerCode())
                .defaultBaseUrl(request.getDefaultBaseUrl())
                .describe(request.getDescribe())
                .icon(request.getIcon())
                .enable(request.getEnable())
                .build();
        modelManufacturerService.save(entity);
        return Results.success(convertToVO(entity));
    }

    /**
     * 更新模型厂商
     * @param request 更新请求参数，包含厂商ID及需要更新的字段
     * @return 更新后的厂商信息
     */
    @PutMapping("/manufacturer/update")
    public Result<ModelManufacturerVO> update(@RequestBody UpdateModelManufacturerRequest request) {
        ModelManufacturerDO entity = modelManufacturerService.getById(request.getId());
        if (entity == null) {
            return Results.success(null);
        }
        BeanUtils.copyProperties(request, entity);
        modelManufacturerService.updateById(entity);
        return Results.success(convertToVO(entity));
    }

    /**
     * 删除模型厂商
     * @param id 厂商ID
     * @return 无返回数据
     */
    @DeleteMapping("/manufacturer/delete")
    public Result<Void> delete(@RequestParam("id") Integer id) {
        modelManufacturerService.removeById(id);
        return Results.success();
    }

    /**
     * 根据ID查询单个模型厂商
     * @param id 厂商ID
     * @return 厂商详细信息
     */
    @GetMapping("/manufacturer/get")
    public Result<ModelManufacturerVO> getById(@RequestParam("id") Integer id) {
        ModelManufacturerDO entity = modelManufacturerService.getById(id);
        if (entity == null) {
            return Results.success(null);
        }
        return Results.success(convertToVO(entity));
    }

    /**
     * 查询所有模型厂商列表
     * @return 厂商列表
     */
    @GetMapping("/manufacturer/list")
    public Result<List<ModelManufacturerVO>> list() {
        List<ModelManufacturerDO> entities = modelManufacturerService.list();
        List<ModelManufacturerVO> voList = entities.stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
        return Results.success(voList);
    }

    private ModelManufacturerVO convertToVO(ModelManufacturerDO entity) {
        return ModelManufacturerVO.builder()
                .id(entity.getId())
                .manufacturerName(entity.getManufacturerName())
                .manufacturerCode(entity.getManufacturerCode())
                .defaultBaseUrl(entity.getDefaultBaseUrl())
                .describe(entity.getDescribe())
                .icon(entity.getIcon())
                .enable(entity.getEnable())
                .createTime(entity.getCreateTime())
                .updateTime(entity.getUpdateTime())
                .build();
    }
}