package com.chinatelecom.aigc.evaluate.dto.req;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Accessors(chain = false) // 设置 chain = false
public class QuestionImportReq {

    /**
     * 题目唯一编号
     */
    @ExcelProperty("题目编号")
    private String questionId;
    /**
     * 题目内容
     */
    @ExcelProperty("题目内容")
    private String title;
    /**
     * 所属题库
     */
    @ExcelProperty("所属题库")
    private String category;
    @ExcelProperty("一级标签")
    private String firstTag;
    @ExcelProperty("二级标签")
    private String secondTag;
    /**
     * 题目难度
     */
    @ExcelProperty("题目难度")
    private String difficulty;
    /**
     * 攻击方式
     */
    @ExcelProperty("攻击方式")
    private String attackMethod;

    /**
     * 数据来源
     */
    @ExcelProperty("数据来源")
    private String dataSource;

    private String tags;

    /**
     * 当前数据的行号
     */
    private int rowNum;

    /**
     * 新增数据标识
     */
    private Boolean isCreate = false;

    /**
     * 修改数据标识
     */
    private Boolean isUpdate = false;

    /**
     * 是否需要变更，需要isUpdate=true的情况下
     */
    private Boolean changed = false;
}
