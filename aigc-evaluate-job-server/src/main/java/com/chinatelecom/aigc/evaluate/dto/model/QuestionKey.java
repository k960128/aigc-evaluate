package com.chinatelecom.aigc.evaluate.dto.model;

public class QuestionKey {
    private String questionId;
    private Integer version;

    public QuestionKey(String questionId, Integer version) {
        this.questionId = questionId;
        this.version = version;
    }

    public String getQuestionId() {
        return questionId;
    }

    public Integer getVersion() {
        return version;
    }
}
