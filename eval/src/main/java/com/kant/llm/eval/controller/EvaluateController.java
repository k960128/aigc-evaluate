package com.kant.llm.eval.controller;

import com.kant.llm.eval.client.ModelInfo;
import com.kant.llm.eval.common.convention.Result;
import com.kant.llm.eval.common.enums.ModelManufacturerEnum;
import com.kant.llm.eval.common.web.Results;
import com.kant.llm.eval.dao.entity.EvalResultDetailDO;
import com.kant.llm.eval.dao.entity.EvalTaskDetailDO;
import com.kant.llm.eval.dao.entity.ModelInfoDO;
import com.kant.llm.eval.dao.mapper.EvalTaskDetailMapper;
import com.kant.llm.eval.service.EvalResultDetailService;
import com.kant.llm.eval.service.ModelInfoService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/evaluate")
public class EvaluateController {

    private final EvalWorker evalWorker;
    private final EvalResultDetailService evalResultDetailService;
    private final EvalTaskDetailMapper evalTaskDetailMapper;
    private final ModelInfoService modelInfoService;

    public EvaluateController(EvalWorker evalWorker,
                              EvalResultDetailService evalResultDetailService,
                              EvalTaskDetailMapper evalTaskDetailMapper,
                              ModelInfoService modelInfoService) {
        this.evalWorker = evalWorker;
        this.evalResultDetailService = evalResultDetailService;
        this.evalTaskDetailMapper = evalTaskDetailMapper;
        this.modelInfoService = modelInfoService;
    }

    /**
     * 手动发起单论评测对话
     * @return 手动发起单论评测对话
     */
    @GetMapping("/chat")
    public Result<Void> chat(Long resultDetailId) {
        EvalResultDetailDO resultDetailDO = evalResultDetailService.getById(resultDetailId);
        EvalTaskDetailDO evalTaskDetailDO = evalTaskDetailMapper.selectById(resultDetailDO.getSampleId());
        ModelInfoDO modelInfoDO = modelInfoService.getById(evalTaskDetailDO.getModelId());
        ModelInfo modelInfo = ModelInfo.builder()
                .modelId(modelInfoDO.getId())
                .model(modelInfoDO.getModel())
                .apiKey(modelInfoDO.getApiKey())
                .baseUrl(modelInfoDO.getBaseUrl())
                .manufacturerType(ModelManufacturerEnum.valueOf(modelInfoDO.getManufacturerCode()))
                .build();
        evalWorker.execute(resultDetailDO,evalTaskDetailDO,modelInfo);
        return Results.success();
    }


}
