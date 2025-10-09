package com.chinatelecom.aigc.evaluate.service.impl;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class TagStatisticsResp {
    private String name; // 标签名
    private long count;  // 标签数量
    private String per;
    private String result;
    private String standard;
    private List<TagStatisticsResp> children; // 子标签（可递归嵌套）

    // Getters 和 Setters
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public long getCount() {
        return count;
    }

    public void setCount(long count) {
        this.count = count;
    }
    public String getPer() {
        return per;
    }

    public void setPer(String per) {
        this.per = per;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public String getStandard() {
        return standard;
    }

    public void setStandard(String standard) {
        this.standard = standard;
    }
    public List<TagStatisticsResp> getChildren() {
        return children;
    }

    public void setChildren(List<TagStatisticsResp> children) {
        this.children = children;
    }


    public TagStatisticsResp(String name, long count) {
        this.name = name;
        this.count = count;
    }
}
