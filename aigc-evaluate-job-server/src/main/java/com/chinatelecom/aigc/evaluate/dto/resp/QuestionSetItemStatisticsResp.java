package com.chinatelecom.aigc.evaluate.dto.resp;

import com.chinatelecom.aigc.evaluate.service.impl.TagStatisticsResp;

import java.util.List;
import java.util.Map;

public class QuestionSetItemStatisticsResp {

    // 统计信息字段
    private String name;   // 题目类别名称，例如 "FORWARD" 或 "NEGATIVE"
    private long total;    // 该类别的题目总数
    private long hard;     // 难度为 "hard" 的题目数量
    private long medium;   // 难度为 "medium" 的题目数量
    private long simple;   // 难度为 "simple" 的题目数量

    // Getters 和 Setters
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public long getHard() {
        return hard;
    }

    public void setHard(long hard) {
        this.hard = hard;
    }

    public long getMedium() {
        return medium;
    }

    public void setMedium(long medium) {
        this.medium = medium;
    }

    public long getSimple() {
        return simple;
    }

    public void setSimple(long simple) {
        this.simple = simple;
    }
}
