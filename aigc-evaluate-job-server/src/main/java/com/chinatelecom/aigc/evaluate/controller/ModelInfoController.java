package com.chinatelecom.aigc.evaluate.controller;

import com.chinatelecom.aigc.evaluate.common.pojo.CommonResult;
import com.chinatelecom.aigc.evaluate.common.pojo.PageResult;
import com.chinatelecom.aigc.evaluate.common.util.object.BeanUtils;
import com.chinatelecom.aigc.evaluate.domain.ModelInfoDO;
import com.chinatelecom.aigc.evaluate.dto.req.ModelInfoPageReq;
import com.chinatelecom.aigc.evaluate.dto.req.ModelInfoSaveReq;
import com.chinatelecom.aigc.evaluate.dto.req.ModelInfoUpdateReq;
import com.chinatelecom.aigc.evaluate.dto.resp.ModelInfoResp;
import com.chinatelecom.aigc.evaluate.service.ModelInfoService;
import io.swagger.annotations.Api;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

import static com.chinatelecom.aigc.evaluate.common.pojo.CommonResult.success;

@RestController
@RequestMapping("/aigc/evaluate/model-info")
@Api(tags = "模型管理 - model")
public class ModelInfoController {

    private final ModelInfoService modelInfoService;

    public ModelInfoController(ModelInfoService modelInfoService) {
        this.modelInfoService = modelInfoService;
    }

    @PostMapping("/create")
    @Operation(summary = "创建模型信息")
    public CommonResult<Long> createModelInfo(@Valid @RequestBody ModelInfoSaveReq createReqVO) {
        return success(modelInfoService.createModelInfo(createReqVO));
    }

    @PostMapping("/connect")
    @Operation(summary = "测试模型链接")
    public CommonResult<String> connectModelInfo(@Valid @RequestBody ModelInfoSaveReq createReqVO) {
        String response = modelInfoService.connectModelInfo(createReqVO);
        boolean isSuccess = true;
        if (response == null || response.trim().isEmpty() || response.startsWith("ERROR")) {
            isSuccess = false;
        }

        if (isSuccess) {
            return CommonResult.success("请求成功", response, 0);
        } else {
            return CommonResult.error(-1, response);
        }
    }

    @PutMapping("/update")
    @Operation(summary = "更新模型信息")
    public CommonResult<Boolean> updateModelInfo(@Valid @RequestBody ModelInfoUpdateReq updateReqVO) {
        modelInfoService.updateModelInfo(updateReqVO);
        return CommonResult.success("模型更新成功", 0);
    }

    @GetMapping("/get")
    @Operation(summary = "获取模型信息")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    public CommonResult<ModelInfoResp> getModelInfo(@RequestParam("id") Long id) {
        ModelInfoDO modelInfo = modelInfoService.getModelInfo(id);
        return success(BeanUtils.toBean(modelInfo, ModelInfoResp.class));
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除模型信息")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    public CommonResult<Boolean> deleteModelInfo(@RequestParam("id") Long id) {
        modelInfoService.deleteModelInfo(id);
        return CommonResult.success("模型删除成功", 0);
    }

    @GetMapping("/page")
    @Operation(summary = "获取模型信息分页")
    public CommonResult<PageResult<ModelInfoResp>> getModelInfoPage(@Valid ModelInfoPageReq pageVO) {
        PageResult<ModelInfoDO> pageResult = modelInfoService.getModelInfoPage(pageVO);
        return success(BeanUtils.toBean(pageResult, ModelInfoResp.class));
    }

    @GetMapping("/get-model-template")
    @Operation(summary = "获取模型模板代码")
    public CommonResult<String> getModelTemplate() {
        String template = "'''\n" +
                "请在 get_model_answer 函数中实现待测模型的逻辑\n" +
                "入参：prompt 内容（str）\n" +
                "出参：answer 内容（str）\n" +
                "要求：\n" +
                "1. def get_model_answer(prompt) 的函数定义不能修改\n" +
                "2. 不支持 pip install\n" +
                "3. 请勿留下调试打印\n" +
                "4. 捕捉到的错误使用raise传出去\n" +
                "'''\n\n" +

                "import sys\n" +
                "import json\n" +
                "import ssl\n" +
                "from urllib.error import HTTPError, URLError\n" +
                "import urllib.request\n\n" +

                "def get_model_answer(prompt):\n" +
                "    headers = {\n" +
                "        \"Content-Type\": \"application/json\"\n" +
                "    }\n\n" +
                "    request_data = {\n" +
                "        \"model\": \"deepseek-8b\",\n" +
                "        \"messages\": [\n" +
                "            {\"role\": \"user\", \"content\": prompt}\n" +
                "        ],\n" +
                "        \"max_completion_tokens\": 1500,\n" +
                "        \"stream\": False\n" +
                "    }\n\n" +
                "    request_string = json.dumps(request_data).encode('utf-8')\n" +
                "    model_url = \"http://127.0.0.1/aipaas/chatCompletions\"\n" +
                "    context = ssl._create_unverified_context()\n\n" +
                "    try:\n" +
                "        req = urllib.request.Request(model_url, data=request_string, headers=headers, method=\"POST\")\n" +
                "        with urllib.request.urlopen(req, context=context) as response:\n" +
                "            body = response.read().decode('utf-8')\n" +
                "            if response.status == 200:\n" +
                "                return body\n" +
                "            else:\n" +
                "                raise RuntimeError(f\"HTTP {response.status}: {body}\")\n" +
                "    except BaseException as e:\n" +
                "        raise e\n";

        return CommonResult.success(template);
    }
}
