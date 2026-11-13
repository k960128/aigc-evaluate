package com.kant.llm.eval.newpipeline.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.kant.llm.common.enums.EvalResultStatusEnums;
import com.kant.llm.common.enums.EvalSafeStatusEnums;
import com.kant.llm.common.enums.TaskStatusEnums;
import com.kant.llm.eval.es.service.EvalEsSyncOutboxService;
import com.kant.llm.eval.task.dao.entity.EvalResultDetailDO;
import com.kant.llm.eval.task.dao.mapper.EvalResultDetailMapper;
import com.kant.llm.eval.task.dao.mapper.EvalResultRetryMapper;
import com.kant.llm.eval.task.dao.mapper.EvalTaskDetailMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

import static org.mockito.Mockito.mock;

abstract class ResultExecutionTestDatabase {
    protected JdbcTemplate jdbc;
    protected EvalResultDetailMapper resultMapper;
    protected EvalTaskDetailMapper taskMapper;
    protected EvalResultRetryMapper retryMapper;
    protected TransactionTemplate transactions;
    protected SafetyEvaluateResultWriter writer;
    protected final EvalEsSyncOutboxService outbox = mock(EvalEsSyncOutboxService.class);

    @BeforeEach
    void initializeDatabase() throws Exception {
        DriverManagerDataSource source = new DriverManagerDataSource(
                "jdbc:h2:mem:execution_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000",
                "sa", "");
        source.setDriverClassName("org.h2.Driver");
        jdbc = new JdbcTemplate(source);
        jdbc.execute("""
                CREATE TABLE eval_result_detail (
                  id BIGINT PRIMARY KEY, task_id BIGINT, task_detail_id BIGINT, sample_id BIGINT,
                  input_text VARCHAR(2000), risk_details_id BIGINT, model_output VARCHAR(2000),
                  raw_response VARCHAR(2000), latency INT, is_safe INT, status INT, error_msg VARCHAR(2000),
                  execution_version INT DEFAULT 0 NOT NULL, progress_counted BOOLEAN DEFAULT FALSE NOT NULL,
                  knowledge_version INT, knowledge_version_id BIGINT, create_time TIMESTAMP,
                  update_time TIMESTAMP, deleted BOOLEAN DEFAULT FALSE)
                """);
        jdbc.execute("""
                CREATE TABLE eval_task_detail (
                  id BIGINT PRIMARY KEY, task_id BIGINT, serial_no BIGINT, task_name VARCHAR(200),
                  model_id BIGINT, dataset_id BIGINT, status INT, total_count INT, finished_count INT,
                  failed_count INT, token_usage INT, knowledge_version INT, knowledge_version_id BIGINT,
                  create_time TIMESTAMP, update_time TIMESTAMP, start_time TIMESTAMP, end_time TIMESTAMP,
                  deleted BOOLEAN DEFAULT FALSE)
                """);
        jdbc.execute("""
                CREATE TABLE eval_result_retry (
                  id BIGINT PRIMARY KEY, request_id VARCHAR(64) UNIQUE NOT NULL,
                  result_detail_id BIGINT NOT NULL, task_detail_id BIGINT NOT NULL,
                  execution_version INT NOT NULL, mode VARCHAR(16), start_stage VARCHAR(32), create_time TIMESTAMP)
                """);
        MybatisConfiguration config = new MybatisConfiguration();
        config.setMapUnderscoreToCamelCase(true);
        config.addMapper(EvalResultDetailMapper.class);
        config.addMapper(EvalTaskDetailMapper.class);
        config.addMapper(EvalResultRetryMapper.class);
        MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(source);
        factory.setConfiguration(config);
        SqlSessionTemplate sessions = new SqlSessionTemplate(factory.getObject());
        resultMapper = sessions.getMapper(EvalResultDetailMapper.class);
        taskMapper = sessions.getMapper(EvalTaskDetailMapper.class);
        retryMapper = sessions.getMapper(EvalResultRetryMapper.class);
        transactions = new TransactionTemplate(new DataSourceTransactionManager(source));
        writer = new SafetyEvaluateResultWriter(resultMapper, taskMapper, outbox, transactions);
    }

    @AfterEach
    void shutDownDatabase() {
        if (jdbc != null) {
            jdbc.execute("SHUTDOWN");
        }
    }

    protected void insertTask(int total, int finished, int failed, TaskStatusEnums status) {
        jdbc.update("""
                INSERT INTO eval_task_detail (id, task_id, model_id, status, total_count, finished_count, failed_count)
                VALUES (20, 10, 5, ?, ?, ?, ?)
                """, status.getCode(), total, finished, failed);
    }

    protected void insertResult(Long id, EvalResultStatusEnums status) {
        jdbc.update("""
                INSERT INTO eval_result_detail (id, task_id, task_detail_id, sample_id,
                    risk_details_id, model_output, status, is_safe)
                VALUES (?, 10, 20, 30, 40, 'model output', ?, 0)
                """, id, status.getCode());
    }

    protected EvalResultDetailDO outcome(Long id, EvalResultStatusEnums status) {
        return EvalResultDetailDO.builder().id(id).taskDetailId(20L).status(status.getCode())
                .isSafe(EvalSafeStatusEnums.SAFE.getCode()).build();
    }

    protected Integer taskInt(String column) {
        return jdbc.queryForObject("SELECT " + column + " FROM eval_task_detail WHERE id = 20", Integer.class);
    }
}
