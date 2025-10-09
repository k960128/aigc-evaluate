package com.chinatelecom.aigc.evaluate.controller;

import cn.hutool.core.collection.CollectionUtil;
import com.chinatelecom.aigc.evaluate.common.pojo.CommonResult;
import com.chinatelecom.aigc.evaluate.common.pojo.PageResult;
import com.chinatelecom.aigc.evaluate.dto.req.QuestionSetItemPageReq;
import com.chinatelecom.aigc.evaluate.dto.req.QuestionSetPageByReq;
import com.chinatelecom.aigc.evaluate.dto.resp.QuestionSetItemResp;
import com.chinatelecom.aigc.evaluate.service.QuestionSetItemService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/aigc/evaluate/question_set_item")
@Api(tags = "题目集映射题目管理 - question-set-item")
public class QuestionSetItemController {

    private final QuestionSetItemService questionSetItemService;

    public QuestionSetItemController(QuestionSetItemService questionSetItemService) {
        this.questionSetItemService = questionSetItemService;
    }


    @GetMapping("/list")
    @ApiOperation("根据题集ID集合查询详情题目")
    public CommonResult<List<QuestionSetItemResp>> getQuestionSetItemList(@ApiParam(value = "习题集ID集合", required = true) @RequestParam("ids") List<Long> ids,
                                                                          @ApiParam(value = "是否去重，默认true", defaultValue = "true") @RequestParam(value = "distinct", defaultValue = "true") Boolean distinct) {
        List<QuestionSetItemResp> resultList = questionSetItemService.list(ids, distinct);
        return CommonResult.success(resultList);
    }

    @PostMapping("/search")
    @ApiOperation("分页查询题集详情题目")
    public CommonResult<PageResult<QuestionSetItemResp>> getQuestionSetItemPage(@RequestBody QuestionSetItemPageReq req) {
        PageResult<QuestionSetItemResp> resultPage = questionSetItemService.getQuestionSetItemPage(req);
        return CommonResult.success(resultPage);
    }

    @PostMapping("/searchBySetId")
    @ApiOperation("根据题集ID分页查询")
    public CommonResult<PageResult<QuestionSetItemResp>> searchBySetId(@RequestBody QuestionSetPageByReq req) {
        PageResult<QuestionSetItemResp> resultPage = questionSetItemService.searchBySetIdPage(req);
        return CommonResult.success(resultPage);
    }
}
