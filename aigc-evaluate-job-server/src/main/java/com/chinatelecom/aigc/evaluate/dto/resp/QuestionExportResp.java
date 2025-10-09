package com.chinatelecom.aigc.evaluate.dto.resp;

import com.alibaba.excel.annotation.ExcelProperty;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@Builder
@EqualsAndHashCode(callSuper = false)
@ToString(callSuper = true)
@ApiModel(value = "管理后台 - 题目 Export Resp VO")
public class QuestionExportResp {

    @ExcelProperty("题目唯一编号")
    private String questionId;
    @ExcelProperty("所属题库")
    private String category;
    @ExcelProperty("题目内容")
    private String title;
    @ExcelProperty("一级标签")
    private String firstTag;
    @ExcelProperty("二级标签")
    private String secondTag;
    @ExcelProperty("题目难度")
    private String difficulty;
    @ExcelProperty("攻击方式")
    private String attackMethod;
    @ExcelProperty("数据来源")
    private String dataSource;
}
