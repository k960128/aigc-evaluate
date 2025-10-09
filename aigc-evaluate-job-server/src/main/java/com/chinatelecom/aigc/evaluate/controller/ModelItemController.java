package com.chinatelecom.aigc.evaluate.controller;

import com.chinatelecom.aigc.evaluate.common.pojo.CommonResult;
import com.chinatelecom.aigc.evaluate.common.pojo.PageResult;
import com.chinatelecom.aigc.evaluate.common.util.object.BeanUtils;
import com.chinatelecom.aigc.evaluate.domain.ModelInfoDO;
import com.chinatelecom.aigc.evaluate.domain.ModelItemDO;
import com.chinatelecom.aigc.evaluate.dto.req.ModelInfoPageReq;
import com.chinatelecom.aigc.evaluate.dto.req.ModelItemPageReq;
import com.chinatelecom.aigc.evaluate.dto.resp.ModelInfoResp;
import com.chinatelecom.aigc.evaluate.dto.resp.ModelItemResp;
import com.chinatelecom.aigc.evaluate.service.ModelItemService;
import io.swagger.annotations.Api;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

import java.util.List;
import java.util.stream.Collectors;

import static com.chinatelecom.aigc.evaluate.common.pojo.CommonResult.success;

@RestController
@RequestMapping("/aigc/evaluate/model-item")
@Api(tags = "模型信息管理")
public class ModelItemController {

    private final ModelItemService modelItemService;

    public ModelItemController(ModelItemService modelItemService) {
        this.modelItemService = modelItemService;
    }

    @GetMapping("/get")
    @Operation(summary = "获取模型信息")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    public CommonResult<ModelInfoResp> getModelItem(@RequestParam("id") Long id) {
        ModelItemDO modelInfo = modelItemService.getModelItem(id);
        return success(BeanUtils.toBean(modelInfo, ModelInfoResp.class));
    }

    @GetMapping("/page")
    @Operation(summary = "获取模型信息分页")
    public CommonResult<PageResult<ModelItemResp>> getModelItemPage(@Valid ModelItemPageReq pageVO) {
        PageResult<ModelItemDO> pageResult = modelItemService.getModelItemPage(pageVO);
        return success(BeanUtils.toBean(pageResult, ModelItemResp.class));
    }

    @GetMapping("/list")
    @Operation(summary = "获取所有模型信息")
    public CommonResult<List<ModelItemResp>> getModelItemList() {
        List<ModelItemDO> modelItemList = modelItemService.getAllModelItems();

        // 转换为 ModelItemResp 列表
        List<ModelItemResp> modelItemResps = modelItemList.stream()
                .map(item -> BeanUtils.toBean(item, ModelItemResp.class))
                .collect(Collectors.toList());

        return success(modelItemResps);
    }
}
