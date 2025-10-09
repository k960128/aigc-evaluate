package com.chinatelecom.aigc.evaluate.common.exception.enums;


import com.chinatelecom.aigc.evaluate.common.exception.ErrorCode;

/**
 * System 错误码枚举类
 *
 * system 系统，使用 1-002-000-000 段
 */
public interface ErrorCodeConstants {

    // ========== Excel相关 模块 1-002-100-000 ==========
    ErrorCode EXCEL_DOWNLOAD_ERROR = new ErrorCode(1_002_100_001, "下载模板失败，原因：{}");
    ErrorCode EXCEL_IMPORT_QUESTIONS_ERROR = new ErrorCode(1_002_100_002, "导入失败:[{}]");

    // ========== 题目相关 模块 1-002-200-000 ==========
    ErrorCode QUESTION_ID_EXISTS_ERROR = new ErrorCode(1_002_200_001, "题目唯一编号重复，请更改");
    ErrorCode QUESTION_NOT_EXISTS_ERROR = new ErrorCode(1_002_200_002, "题目不存在");
    ErrorCode QUESTION_DELETE_ERROR = new ErrorCode(1_002_200_003, "删除失败，[{}]");
    ErrorCode QUESTION_BATCH_DELETE_ERROR = new ErrorCode(1_002_200_004, "批量删除失败，[{}]");
    ErrorCode QUESTION_UPDATE_ERROR = new ErrorCode(1_002_200_005, "修改题目失败，[{}]");
    ErrorCode QUESTION_SAVE_ERROR = new ErrorCode(1_002_200_006, "添加题目失败，[{}]");
    // ========== 题目集相关 模块 1-002-300-000 ==========
    ErrorCode QUEUSTION_SET_EXTRACTCOUNT_NULL_ERROR = new ErrorCode(1_002_300_001, "题目数量不能为空");
    ErrorCode QUEUSTION_SET_EXTRACTDIMENSION_NULL_ERROR = new ErrorCode(1_002_300_002, "抽取题目维度不能为空");
    ErrorCode QUEUSTION_SET_EXTRACTDIMENSION_PATTERN_ERROR = new ErrorCode(1_002_300_003, "抽取题目维度只能为RANDOM|CUSTOM");
    ErrorCode QUEUSTION_SET_EXTRACTTYPE_NULL_ERROR = new ErrorCode(1_002_300_004, "自定义类型不能为空，选值1-难度|2-分类");
    ErrorCode QUEUSTION_SET_TYPES_NULL_ERROR = new ErrorCode(1_002_300_005, "抽取题目类型集合不能为空，按难度：（SIMPLE-简单,MEDIUM-中等,HARD-困难），按分类：待定");
    ErrorCode QUEUSTION_SET_NAME_EXISTS_ERROR = new ErrorCode(1_002_300_006, "题目集名称重复");
    ErrorCode QUEUSTION_SET_NOT_EXISTS_ERROR = new ErrorCode(1_002_300_007, "题目集不存在");
    ErrorCode QUEUSTION_SET_GENERATE_ERROR = new ErrorCode(1_002_300_008, "题目集已经生成，不允许重复生成");
    ErrorCode QUEUSTION_SET_UPDATE_ERROR = new ErrorCode(1_002_300_009, "暂不支持修改");
    ErrorCode QUEUSTION_SET_GENERATE_ITEM_ERROR = new ErrorCode(1_002_300_010, "生成题目失败：[{}]");
    ErrorCode QUEUSTION_SET_GENERATE_STRATEGY_ERROR = new ErrorCode(1_002_300_011, "暂不支持的题目抽取类型：[{}]");
    ErrorCode QUEUSTION_SET_NAME_NULL_ERROR = new ErrorCode(1_002_300_012, "题目集名称不能为空");
    // ========== 标签相关 模块 1-002-400-000 ==========
    ErrorCode QUESTION_TAG_NAME_EXISTS_ERROR = new ErrorCode(1_002_400_001, "标签名称重复");
    ErrorCode QUESTION_TAG_NOT_EXISTS_ERROR = new ErrorCode(1_002_400_002, "标签不存在");

    // ========== 模型返回结果相关 1_002_500_000 ==========
    ErrorCode TASK_ANSWER_NOT_EXISTS_ERROR = new ErrorCode(1_002_500_001, "模型返回结果数据不存在");

    ErrorCode TASK_ANSWER_UPDATE_ERROR = new ErrorCode(1_002_500_001, "更新失败");

    // ========== 模型相关 1_002_700_000 ==========
    ErrorCode MODEL_HANDLER_NOT_EXISTS_ERROR = new ErrorCode(1_002_700_001, "此模型处理函数不存在");

    ErrorCode MODEL_APIKEY_ENCRYPT_ERROR = new ErrorCode(1_002_700_002, "API Key 加密失败");

    ErrorCode MODEL_NOT_EXISTS_ERROR = new ErrorCode(1_002_700_003, "此模型不存在");

    // ========== 模型返回结果相关 1_002_800_000 ==========
    ErrorCode REPORT_CREATE_ERROR = new ErrorCode(1_002_700_001, "创建失败，未匹配到任务和模型");
    ErrorCode REPORT_PREVIEW_ERROR = new ErrorCode(1_002_700_002, "预览失败，报告不存在");
    ErrorCode REPORT_DOWNLOAD_ERROR = new ErrorCode(1_002_700_003, "下载失败，报告不存在");
    ErrorCode REPORT_GENERATE_ERROR = new ErrorCode(1_002_700_004, "报告生成失败，失败原因:[{}]");
    ErrorCode REPORT_TYPE_ERROR = new ErrorCode(1_002_700_005, "不支持的报告类型:[{}]");
    ErrorCode REPORT_NOT_FOUND = new ErrorCode(1_002_700_006, "报告未找到，ID:[{}]");

}

