package com.chinatelecom.aigc.evaluate.dto.resp;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = false)
@ToString(callSuper = true)
@ApiModel(value = "管理后台 - 批量导入 Response VO")
public class QuestionImportResp {
    @ApiModelProperty(value = "导入批次号")
    private Long batchNo;
    // 导入题目总数量
    @ApiModelProperty(value = "导入题目总数量")
    private String imported_count = "0";
    // 修改题目成功数量
    @ApiModelProperty(value = "修改题目成功数量")
    private String update_count = "0";
    // 新增题目成功数量
    @ApiModelProperty(value = "新增题目成功数量")
    private String add_count = "0";
    // 无需变更的题目数量
    @ApiModelProperty(value = "无需变更的题目数量")
    private String not_change_count = "0";
    // 失败题目数量
    @ApiModelProperty(value = "失败题目数量")
    private String failed_count = "0";
    // 失败题目的行号
    @ApiModelProperty(value = "失败题目的行号")
    Map<String,String> failed_rows;
}
