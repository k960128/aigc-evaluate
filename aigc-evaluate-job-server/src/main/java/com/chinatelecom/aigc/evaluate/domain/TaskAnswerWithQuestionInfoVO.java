package com.chinatelecom.aigc.evaluate.domain;

import lombok.Data;

@Data
public class TaskAnswerWithQuestionInfoVO {
    // TaskAnswerDO 部分
    private Long id;
    private Long modelId;
    private String questionId;
    private Integer questionVersion;
    private String questionContent;
    private String answerContent;
    private String questionCategory;
    private Integer judgeResult;
    private Integer violation;
    private String thinkProcess;
    private String modelName;
    private String modelVersion;
    private String appName;
    private Long questionSet;
    private Long taskId;

    // QuestionDO 补充信息
    private String attackMethod;
    private String tags;
    private String difficulty;
    private String dataSource;
    private String questionDesc;
}


