package com.chinatelecom.aigc.evaluate.controller;

import com.chinatelecom.aigc.evaluate.common.pojo.CommonResult;
import com.chinatelecom.aigc.evaluate.common.util.object.BeanUtils;
import com.chinatelecom.aigc.evaluate.domain.QuestionTagInfoDO;
import com.chinatelecom.aigc.evaluate.dto.req.QuestionTagReq;
import com.chinatelecom.aigc.evaluate.dto.req.QuestionTagSaveReq;
import com.chinatelecom.aigc.evaluate.dto.req.QuestionTagUpdateReq;
import com.chinatelecom.aigc.evaluate.dto.resp.QuestionTagContainQuestionResp;
import com.chinatelecom.aigc.evaluate.dto.resp.QuestionTagResp;
import com.chinatelecom.aigc.evaluate.service.QuestionTagService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiParam;
import io.swagger.v3.oas.annotations.Operation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/aigc/evaluate/tag")
@Api(tags = "标签管理 - tag")
public class QuestionTagController {

    private final QuestionTagService questionTagService;

    public QuestionTagController(QuestionTagService questionTagService) {
        this.questionTagService = questionTagService;
    }

    @PostMapping("/create")
    @Operation(summary = "创建标签")
    public CommonResult<Boolean> create(@Valid @RequestBody QuestionTagSaveReq param) {
        questionTagService.create(param);
        return CommonResult.success(true);
    }

    @PostMapping("/update")
    @Operation(summary = "编辑")
    public CommonResult<Boolean> update(@Valid @RequestBody QuestionTagUpdateReq param) {
        questionTagService.update(param);
        return CommonResult.success(true);
    }

    @GetMapping("/getByTagId")
    @Operation(summary = "根据标签ID获取标签(不包含子标签)")
    public CommonResult<QuestionTagResp> get(@ApiParam(value = "标签ID", required = true)
                                             @RequestParam("tagId") String tagId) {
        return CommonResult.success(BeanUtils.toBean(questionTagService.getByTagId(tagId), QuestionTagResp.class));
    }

    //TODO 改造获取题目数量
    @GetMapping("/getByTagIdContainChild")
    @Operation(summary = "根据标签ID获取标签(包含子标签集合)")
    public CommonResult<QuestionTagResp> getByTagIdContainChild(@ApiParam(value = "标签ID", required = true)
                                                                @RequestParam("tagId") String tagId) {
        QuestionTagResp questionTagResp = questionTagService.getByTagIdContainChild(tagId);
        return CommonResult.success(BeanUtils.toBean(questionTagResp, QuestionTagResp.class));
    }

    @PostMapping("/list")
    @Operation(summary = "获取标签集合")
    public CommonResult<List<QuestionTagResp>> list(@Valid @RequestBody QuestionTagReq param) {
        return CommonResult.success(questionTagService.list(param));
    }

    @GetMapping("/listTree")
    @Operation(summary = "获取标签树")
    public CommonResult<List<QuestionTagResp>> listTree() {
        return CommonResult.success(questionTagService.listTree());
    }
    @GetMapping("/listContainQuestion")
    @Operation(summary = "根据标签ID获取题目集合")
    public CommonResult<QuestionTagContainQuestionResp> listContainQuestion(@ApiParam(value = "标签ID", required = true)
                                                                            @RequestParam("tagId") String tagId) {

        return CommonResult.success(questionTagService.listContainQuestion(tagId));
    }

}
