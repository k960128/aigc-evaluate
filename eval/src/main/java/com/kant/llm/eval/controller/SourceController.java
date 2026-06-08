package com.kant.llm.eval.controller;

import com.kant.llm.eval.client.*;
import com.kant.llm.eval.common.convention.Result;
import com.kant.llm.eval.common.enums.ModelManufacturerEnum;
import com.kant.llm.eval.common.exception.ServiceException;
import com.kant.llm.eval.common.web.Results;
import com.kant.llm.eval.dao.entity.ModelInfoDO;
import com.kant.llm.eval.dao.entity.ModelManufacturerDO;
import com.kant.llm.eval.dto.req.CreateModelInfoRequest;
import com.kant.llm.eval.dto.req.CreateModelManufacturerRequest;
import com.kant.llm.eval.dto.req.ModelConnectivityTestRequest;
import com.kant.llm.eval.dto.req.UpdateModelInfoRequest;
import com.kant.llm.eval.dto.req.UpdateModelManufacturerRequest;
import com.kant.llm.eval.dto.resp.ModelInfoVO;
import com.kant.llm.eval.dto.resp.ModelManufacturerVO;
import com.kant.llm.eval.service.ModelInfoService;
import com.kant.llm.eval.service.ModelManufacturerService;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/source")
public class SourceController {

    private final ModelManufacturerService modelManufacturerService;
    private final ModelInfoService modelInfoService;
    private final ModelClientStrategyFactory modelClientStrategyFactory;

    public SourceController(ModelManufacturerService modelManufacturerService,
                            ModelInfoService modelInfoService,
                            ModelClientStrategyFactory modelClientStrategyFactory) {
        this.modelManufacturerService = modelManufacturerService;
        this.modelInfoService = modelInfoService;
        this.modelClientStrategyFactory = modelClientStrategyFactory;
    }

    /**
     * 创建模型厂商
     *
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
     *
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
     *
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
     *
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
     *
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

    /**
     * 创建模型信息
     *
     * @param request 创建请求参数，包含模型名称、URL、密钥等信息
     * @return 创建成功的模型信息
     */
    @PostMapping("/model/create")
    public Result<ModelInfoVO> createModel(@RequestBody CreateModelInfoRequest request) {
        ModelInfoDO entity = ModelInfoDO.builder()
                .model(request.getModel())
                .baseUrl(request.getBaseUrl())
                .apiKey(request.getApiKey())
                .manufacturerCode(request.getManufacturerCode())
                .modelDescribe(request.getModelDescribe())
                .maxThreadSize(request.getMaxThreadSize())
                .maxCompletionTokens(request.getMaxCompletionTokens())
                .stream(request.getStream())
                .config(request.getConfig())
                .status(true)
                .build();
        modelInfoService.save(entity);
        return Results.success(convertToModelInfoVO(entity));
    }

    /**
     * 更新模型信息
     *
     * @param request 更新请求参数，包含模型ID及需要更新的字段
     * @return 更新后的模型信息
     */
    @PutMapping("/model/update")
    public Result<ModelInfoVO> updateModel(@RequestBody UpdateModelInfoRequest request) {
        ModelInfoDO entity = modelInfoService.getById(request.getId());
        if (entity == null) {
            return Results.success(null);
        }
        BeanUtils.copyProperties(request, entity);
        modelInfoService.updateById(entity);
        return Results.success(convertToModelInfoVO(entity));
    }

    /**
     * 删除模型信息
     *
     * @param id 模型ID
     * @return 无返回数据
     */
    @DeleteMapping("/model/delete")
    public Result<Void> deleteModel(@RequestParam("id") Long id) {
        modelInfoService.removeById(id);
        return Results.success();
    }

    /**
     * 根据ID查询单个模型信息
     *
     * @param id 模型ID
     * @return 模型详细信息
     */
    @GetMapping("/model/get")
    public Result<ModelInfoVO> getModelById(@RequestParam("id") Long id) {
        ModelInfoDO entity = modelInfoService.getById(id);
        if (entity == null) {
            return Results.success(null);
        }
        return Results.success(convertToModelInfoVO(entity));
    }

    /**
     * 查询所有模型信息列表
     *
     * @return 模型列表
     */
    @GetMapping("/model/list")
    public Result<List<ModelInfoVO>> listModel() {
        List<ModelInfoDO> entities = modelInfoService.list();
        List<ModelInfoVO> voList = entities.stream()
                .map(this::convertToModelInfoVO)
                .collect(Collectors.toList());
        return Results.success(voList);
    }

    /**
     * 转换模型厂商实体为VO
     *
     * @param entity 模型厂商实体
     * @return 厂商VO对象
     */
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

    /**
     * 转换模型信息实体为VO
     *
     * @param entity 模型信息实体
     * @return 模型VO对象
     */
    private ModelInfoVO convertToModelInfoVO(ModelInfoDO entity) {
        return ModelInfoVO.builder()
                .id(entity.getId())
                .model(entity.getModel())
                .baseUrl(entity.getBaseUrl())
                .apiKey(entity.getApiKey())
                .manufacturerCode(entity.getManufacturerCode())
                .modelDescribe(entity.getModelDescribe())
                .maxThreadSize(entity.getMaxThreadSize())
                .maxCompletionTokens(entity.getMaxCompletionTokens())
                .stream(entity.getStream())
                .status(entity.getStatus())
                .config(entity.getConfig())
                .version(entity.getVersion())
                .createTime(entity.getCreateTime())
                .updateTime(entity.getUpdateTime())
                .build();
    }

    /**
     * 大模型连通性测试
     *
     * @param request 测试请求参数，包含模型信息和测试消息
     * @return 测试结果，包含响应内容和耗时
     */
    @PostMapping("/model/testConnectivity")
    public Result<ModelConnectionResponse> testModelConnectivity(@RequestBody ModelConnectivityTestRequest request) {
        // 转换 manufacturerCode 为 ModelManufacturerEnum
        ModelManufacturerEnum manufacturerEnum;
        try {
            manufacturerEnum = ModelManufacturerEnum.valueOf(request.getManufacturerCode().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ServiceException("不支持的模型厂商：" + request.getManufacturerCode());
        }
        // 构建 ModelInfo
        ModelInfo modelInfo = ModelInfo.builder()
                .model(request.getModel())
                .apiKey(request.getApiKey())
                .baseUrl(request.getBaseUrl())
                .manufacturerType(manufacturerEnum)
                .modelId(request.getModelId())
                .build();
        ModelRequest modelRequest = ModelRequest.builder()
                .modelInfo(modelInfo)
                .build();
        ModelClientStrategy strategy = modelClientStrategyFactory.getStrategy(modelInfo);
        ModelConnectionResponse response = strategy.connection(modelRequest);
        return Results.success(response);
    }
}