package com.chinatelecom.aigc.evaluate.controller;


import cn.hutool.core.bean.BeanUtil;
import com.chinatelecom.aigc.evaluate.common.exception.enums.ErrorCodeConstants;
import com.chinatelecom.aigc.evaluate.common.pojo.CommonResult;
import com.chinatelecom.aigc.evaluate.common.pojo.PageResult;
import com.chinatelecom.aigc.evaluate.common.util.object.BeanUtils;
import com.chinatelecom.aigc.evaluate.dto.req.QuestionExportReq;
import com.chinatelecom.aigc.evaluate.dto.req.QuestionPageReq;
import com.chinatelecom.aigc.evaluate.dto.req.QuestionSaveReq;
import com.chinatelecom.aigc.evaluate.dto.req.QuestionUpdateReq;
import com.chinatelecom.aigc.evaluate.dto.resp.QuestionBatchResp;
import com.chinatelecom.aigc.evaluate.dto.resp.QuestionImportResp;
import com.chinatelecom.aigc.evaluate.dto.resp.QuestionResp;
import com.chinatelecom.aigc.evaluate.dto.resp.QuestionSaveResp;
import com.chinatelecom.aigc.evaluate.service.QuestionService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.util.List;

import static com.chinatelecom.aigc.evaluate.common.exception.util.ServiceExceptionUtil.exception;

@Slf4j
@RestController
@RequestMapping("/aigc/evaluate/question")
@Api(tags = "题目管理 - question")
public class QuestionController {

    private final QuestionService questionService;

    public QuestionController(QuestionService questionService) {
        this.questionService = questionService;
    }

    @PostMapping("/forward/create")
    @ApiOperation("新增正向题目")
    public CommonResult<QuestionSaveResp> createForward(@Valid @RequestBody QuestionSaveReq req) {
        return CommonResult.success(BeanUtil.copyProperties(questionService.create(req), QuestionSaveResp.class));
    }

    @PostMapping("/negative/create")
    @ApiOperation("新增负向题目")
    public CommonResult<QuestionSaveResp> createNegative(@Valid @RequestBody QuestionSaveReq req) {
        return CommonResult.success(BeanUtil.copyProperties(questionService.create(req), QuestionSaveResp.class));
    }

    @PutMapping("/forward/update")
    @ApiOperation("修改正向题目")
    public CommonResult<Boolean> updateForward(@Valid @RequestBody QuestionUpdateReq req) {
        int rows = questionService.update(req);
        return CommonResult.success(true, rows == 0 ? "无修改变动" : "修改成功", 0);
    }

    @PutMapping("/negative/update")
    @ApiOperation("修改负向题目")
    public CommonResult<Boolean> updateNegative(@Valid @RequestBody QuestionUpdateReq req) {
        int rows = questionService.update(req);
        return CommonResult.success(true, rows == 0 ? "无修改变动" : "修改成功", 0);
    }

    @DeleteMapping("/forward/delete")
    @ApiOperation("删除正向题目")
    public CommonResult<Void> deleteForward(@ApiParam(value = "题目唯一ID", required = true) @RequestParam("questionId") String questionId) {
        int row = questionService.delete(questionId);
        log.info("删除成功，受影响行数：{}", row);
        return CommonResult.success("删除成功", 0);
    }

    @DeleteMapping("/negative/delete")
    @ApiOperation("删除负向题目")
    public CommonResult<Void> deleteNegative(@ApiParam(value = "题目唯一ID", required = true) @RequestParam("questionId") String questionId) {
        int row = questionService.delete(questionId);
        log.info("删除成功，受影响行数：{}", row);
        return CommonResult.success("删除成功", 0);
    }

    @PostMapping("/forward/batch_delete")
    @ApiOperation("批量删除正向题目")
    public CommonResult<QuestionBatchResp> batchDeleteForward(@RequestBody List<String> questionIds) {
        return CommonResult.success(questionService.batchDelete(questionIds), "批量删除成功", 0);
    }

    @PostMapping("/negative/batch_delete")
    @ApiOperation("批量删除负向题目")
    public CommonResult<QuestionBatchResp> batchDeleteNegative(@RequestBody List<String> questionIds) {
        return CommonResult.success(questionService.batchDelete(questionIds), "批量删除成功", 0);
    }

        @GetMapping("/forward/get")
    @ApiOperation("根据题目ID查询正向数据")
    public CommonResult<QuestionResp> getForward(@ApiParam(value = "题目唯一ID", required = true) @RequestParam("questionId") String questionId) {
        return CommonResult.success(BeanUtil.copyProperties(questionService.getByQuestion(questionId), QuestionResp.class));
    }

    @GetMapping("/negative/get")
    @ApiOperation("根据题目ID查询负向数据")
    public CommonResult<QuestionResp> getNegative(@ApiParam(value = "题目唯一ID", required = true) @RequestParam("questionId") String questionId) {
        return CommonResult.success(BeanUtil.copyProperties(questionService.getByQuestion(questionId), QuestionResp.class));
    }

    @PostMapping("/page")
    @ApiOperation("获得题目分页")
    public CommonResult<PageResult<QuestionResp>> getQuestionPage(@RequestBody QuestionPageReq questionPageReq) {
        return CommonResult.success(BeanUtils.toBean(questionService.getQuestionPage(questionPageReq), QuestionResp.class));
    }

    @PostMapping("/batch_import")
    @ApiOperation("导入题目")
    public CommonResult<QuestionImportResp> importExcel(@RequestPart("file") @RequestParam("file") MultipartFile file) {
        // 参照模板，从第三行开始读取
        return CommonResult.success(questionService.importQuestionList(file), "导入成功！", 0);
    }

    @PostMapping("/export")
    @ApiOperation("导出题目")
    public void exportExcel(@RequestBody QuestionExportReq questionExportReq,
                            HttpServletResponse response) {
        questionService.exportExcel(questionExportReq, response);
    }

    @GetMapping("/template")
    @ApiOperation("获得正向评测题库导入模板")
    public void importTemplate(HttpServletResponse response) {
        InputStream inputStream = null;
        OutputStream outputStream = null;
        try {
            ClassPathResource resource = new ClassPathResource("测评题库导入模板.xlsm");
            inputStream = resource.getInputStream();
            // 处理文件
            response.setHeader("Content-Disposition", "attachment;filename=" + URLEncoder.encode(resource.getFilename(), "utf-8"));
            response.setHeader("content-Type", "application/octet-stream;charset=utf-8");
            outputStream = response.getOutputStream();
            int len = 0;
            byte[] buffer = new byte[1024];
            while ((len = inputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, len);
            }
        } catch (IOException e) {
            log.error("模板下载失败，失败原因:{}", e);
            throw exception(ErrorCodeConstants.EXCEL_DOWNLOAD_ERROR, e);
        } finally {
            try {
                if (outputStream != null) {
                    outputStream.close();
                }
                if (inputStream != null) {
                    inputStream.close();
                }
            } catch (Exception e) {
                e.getMessage();
            }
        }
    }
}
