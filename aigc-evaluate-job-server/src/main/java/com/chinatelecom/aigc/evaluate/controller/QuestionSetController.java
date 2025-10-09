package com.chinatelecom.aigc.evaluate.controller;

import cn.hutool.core.collection.CollectionUtil;
import com.alibaba.fastjson.JSON;
import com.chinatelecom.aigc.evaluate.annotation.Idempotent;
import com.chinatelecom.aigc.evaluate.common.enums.QuestionCategoryEnum;
import com.chinatelecom.aigc.evaluate.common.pojo.CommonResult;
import com.chinatelecom.aigc.evaluate.common.pojo.PageResult;
import com.chinatelecom.aigc.evaluate.common.util.object.BeanUtils;
import com.chinatelecom.aigc.evaluate.domain.QuestionSetInfoDO;
import com.chinatelecom.aigc.evaluate.dto.model.ExtractConf;
import com.chinatelecom.aigc.evaluate.dto.req.QuestionSetPageReq;
import com.chinatelecom.aigc.evaluate.dto.req.QuestionSetSaveReq;
import com.chinatelecom.aigc.evaluate.dto.req.QuestionSetUpdateReq;
import com.chinatelecom.aigc.evaluate.dto.resp.QuestionSetItemStatisticsResp;
import com.chinatelecom.aigc.evaluate.dto.resp.QuestionSetResp;
import com.chinatelecom.aigc.evaluate.dto.resp.QuestionSetSaveResp;
import com.chinatelecom.aigc.evaluate.service.JobService;
import com.chinatelecom.aigc.evaluate.service.QuestionSetItemService;
import com.chinatelecom.aigc.evaluate.service.QuestionSetService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/aigc/evaluate/question_set")
@Api(tags = "题目集管理 - question-set")
public class QuestionSetController {

    private final QuestionSetService questionSetService;
    private final QuestionSetItemService questionSetItemService;
    private final JobService jobService;

    public QuestionSetController(QuestionSetService questionSetService,
                                 QuestionSetItemService questionSetItemService,
                                 JobService jobService) {
        this.questionSetService = questionSetService;
        this.questionSetItemService = questionSetItemService;
        this.jobService = jobService;
    }

    @PostMapping("/create")
    @ApiOperation("新增题目集")
    @Idempotent
    public CommonResult<QuestionSetSaveResp> create(@Valid @RequestBody QuestionSetSaveReq req) {
        QuestionSetInfoDO questionSetInfoDO = questionSetService.create(req);
        return CommonResult.success(BeanUtils.toBean(questionSetInfoDO, QuestionSetSaveResp.class));
    }

    @DeleteMapping("/delete")
    @ApiOperation("删除题集")
    public CommonResult<Void> delete(@ApiParam(value = "题集ID", required = true, example = "1") @RequestParam("id") Long id) {

        List<String> jobNames = jobService.getJobIdsByQuestionSetId(id);
        if (!jobNames.isEmpty()) {
            return CommonResult.error(-1, "题集正在被以下任务使用: " + jobNames);
        }

        questionSetService.delete(id);
        return CommonResult.success("删除成功", 0);
    }

    @PostMapping("/update")
    @ApiOperation("修改题集")
    @Idempotent
    public CommonResult<Void> update(@Valid @RequestBody QuestionSetUpdateReq req) {
        questionSetService.update(req);
        return CommonResult.success("修改成功", 0);
    }

    @GetMapping("/get")
    @ApiOperation("获取题集")
    public CommonResult<QuestionSetResp> get(@ApiParam(value = "题集ID", required = true, example = "1") @RequestParam("id") Long id) {
        // 获取基础配置信息
        QuestionSetInfoDO questionSetInfoDO = questionSetService.get(id);

        QuestionSetResp questionSetResp = new QuestionSetResp();
        questionSetResp.setId(questionSetInfoDO.getId());
        questionSetResp.setQuestionCategory(Arrays.asList(questionSetInfoDO.getQuestionCategory().split(",")));
        questionSetResp.setQuestionSetName(questionSetInfoDO.getQuestionSetName());
        questionSetResp.setDescription(questionSetInfoDO.getDescription());
        questionSetResp.setEvaluationTarget(questionSetInfoDO.getEvaluationTarget());
        // 获取实际题目数量
        ExtractConf extractConf = JSON.parseObject(questionSetInfoDO.getExtractConf(), ExtractConf.class);
        questionSetResp.setExtractConf(extractConf);
        questionSetResp.setExtractCount(questionSetItemService.getQuestionSetItemCountBySetId(id));
        questionSetResp.setForwardQeustionIdList(questionSetItemService.getQuestionIdBySetIdGroupCategory(id, QuestionCategoryEnum.FORWARD.name()));
        questionSetResp.setNegativeQeustionIdList(questionSetItemService.getQuestionIdBySetIdGroupCategory(id, QuestionCategoryEnum.NEGATIVE.name()));
        questionSetResp.setCreateTime(questionSetInfoDO.getCreateTime());
        questionSetResp.setUpdateTime(questionSetInfoDO.getUpdateTime());
        return CommonResult.success(questionSetResp);
    }

    @PostMapping("/search")
    @ApiOperation("分页查询题集")
    public CommonResult<PageResult<QuestionSetResp>> getQuestionSetPage(@RequestBody QuestionSetPageReq req) {
        PageResult<QuestionSetInfoDO> page = questionSetService.getQuestionSetPage(req);
        PageResult<QuestionSetResp> resultPage = new PageResult<>();
        if (CollectionUtil.isNotEmpty(page.getList())) {
            Map<Long, Integer> questionSetItemCountBySetIds = questionSetItemService.getQuestionSetItemCountBySetIds(page.getList().stream().map(QuestionSetInfoDO::getId).collect(Collectors.toList()));
            // 重新组装返回参数
            List<QuestionSetResp> questionSetResps = page.getList().stream().map(info -> {
                QuestionSetResp questionSetResp = new QuestionSetResp();
                questionSetResp.setId(info.getId());
                questionSetResp.setQuestionCategory(Arrays.asList(info.getQuestionCategory().split(",")));
                questionSetResp.setQuestionSetName(info.getQuestionSetName());
                questionSetResp.setDescription(info.getDescription());
                questionSetResp.setEvaluationTarget(info.getEvaluationTarget());
                ExtractConf extractConf = JSON.parseObject(info.getExtractConf(), ExtractConf.class);
                questionSetResp.setExtractConf(extractConf);
                questionSetResp.setExtractCount(questionSetItemCountBySetIds.getOrDefault(info.getId(), 0));
                questionSetResp.setCreateTime(info.getCreateTime());
                questionSetResp.setUpdateTime(info.getUpdateTime());
                return questionSetResp;
            }).collect(Collectors.toList());
            resultPage.setList(questionSetResps);
            resultPage.setTotal(page.getTotal());
        }
        return CommonResult.success(resultPage);
    }

    @GetMapping("/execute")
    @ApiOperation("执行-生成题目集")
    @Idempotent
    public CommonResult<Void> execute(@ApiParam(value = "题集ID", required = true, example = "1") @RequestParam("id") Long id) {
        questionSetService.execute(id);
        return CommonResult.success("执行成功", 0);
    }


    @GetMapping("/statistics")
    @ApiOperation("获取题集统计数据")
    public CommonResult<List<QuestionSetItemStatisticsResp>> getQuestionSetStatistics(
            @ApiParam(value = "题集ID列表", required = true, example = "[1, 2, 3]")
            @RequestParam("ids") List<Long> ids,
            @ApiParam(value = "是否去重", required = false, example = "true")
            @RequestParam(value = "distinct", defaultValue = "false") Boolean distinct) {

        List<QuestionSetItemStatisticsResp> statistics = questionSetItemService.getQuestionSetStatistics(ids, distinct);
        return CommonResult.success(statistics);
    }

}
