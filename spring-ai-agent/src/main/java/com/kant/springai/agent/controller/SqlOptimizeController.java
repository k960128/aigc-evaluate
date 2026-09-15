package com.kant.springai.agent.controller;

import com.kant.springai.agent.agent.SqlOptimizeAgent;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/sql-optimize")
public class SqlOptimizeController {

    private final SqlOptimizeAgent sqlOptimizeAgent;

    public SqlOptimizeController(SqlOptimizeAgent sqlOptimizeAgent) {
        this.sqlOptimizeAgent = sqlOptimizeAgent;
    }

    /**
     * 提交 SQL 优化请求，生成最终 SQL 优化结果。
     * @param request SQL 优化请求
     * @return SQL 优化结果
     */
    @PostMapping("/sqlOptimize")
    public SqlOptimizeAgent.RefinedResponse optimize(@RequestBody SqlOptimizeRequest request) {
        if (request == null || StringUtils.isBlank(request.sql())) {
            throw new IllegalArgumentException("原始 SQL 不能为空");
        }

        return sqlOptimizeAgent.optimize(request);
    }


    /**
     * SQL 优化请求对象。
     * @param sql 原始 SQL
     * @param schema 表结构信息
     * @param indexes 已有索引信息
     * @param businessContext 业务背景
     * @param maxRounds 最大优化轮次，不传默认 3
     */
    public record SqlOptimizeRequest(
            String sql,
            String schema,
            String indexes,
            String businessContext,
            Integer maxRounds) {
    }
}
