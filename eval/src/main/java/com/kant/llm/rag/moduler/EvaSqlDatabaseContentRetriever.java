package com.kant.llm.rag.moduler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.FileCopyUtils;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 基于 Text2SQL 的结构化数据库内容检索器。
 *
 * <p>该 Retriever 用于 RAG 路由到 {@code relational_db} 时，从关系型数据库中查询安全评测资产数据。
 * 它会先使用 {@code prompts/text_2_sql.txt} 和 {@code sql/retrieve_tables.sql} 让大模型生成 SQL，
 * 再由后端做只读校验、表白名单校验和行数限制，最后将查询结果包装为 Spring AI {@link Document}。</p>
 *
 * <p>安全边界：</p>
 * <ul>
 *     <li>只允许执行 SELECT / WITH 开头的只读查询。</li>
 *     <li>拒绝 INSERT、UPDATE、DELETE、DROP、ALTER 等写操作或 DDL 操作。</li>
 *     <li>只允许访问 {@code retrieve_tables.sql} 中声明的表。</li>
 *     <li>自动追加最大行数限制，避免一次查询返回过多数据。</li>
 * </ul>
 */
@Slf4j
public class EvaSqlDatabaseContentRetriever implements DocumentRetriever {

    private static final String TEXT_TO_SQL_PROMPT_PATH = "classpath:/prompts/text_2_sql.txt";
    private static final String TABLE_STRUCTURE_PATH = "classpath:/sql/retrieve_tables.sql";
    private static final int DEFAULT_MAX_ROWS = 50;
    private static final int MAX_CELL_TEXT_LENGTH = 200;

    private static final Pattern CREATE_TABLE_PATTERN =
            Pattern.compile("(?i)CREATE\\s+TABLE\\s+`?([a-zA-Z0-9_]+)`?");
    private static final Pattern TABLE_REFERENCE_PATTERN =
            Pattern.compile("(?i)\\b(?:FROM|JOIN)\\s+`?([a-zA-Z0-9_]+)`?");
    private static final Pattern LIMIT_PATTERN =
            Pattern.compile("(?i)\\bLIMIT\\s+\\d+(\\s*,\\s*\\d+)?\\s*$");
    private static final Pattern FORBIDDEN_SQL_PATTERN = Pattern.compile("""
            (?ix)
            \\b(
                insert|update|delete|drop|alter|truncate|create|replace|merge|
                call|exec|execute|grant|revoke|lock|unlock|set|use|load|outfile|infile
            )\\b
            """);

    private final ChatClient chatClient;
    private final JdbcTemplate jdbcTemplate;
    private final int maxRows;
    private final String textToSqlPrompt;
    private final String databaseStructure;
    private final Set<String> allowedTables;

    /**
     * 无参构造器仅用于旧示例代码兼容。
     *
     * <p>没有注入 {@link ChatClient} 和 {@link DataSource} 时，Retriever 无法真正生成和执行 SQL，
     * {@link #retrieve(Query)} 会直接返回空结果。</p>
     */
    public EvaSqlDatabaseContentRetriever() {
        this(null, null);
    }

    /**
     * 构造 Text2SQL 数据库检索器。
     *
     * @param chatClient 大模型客户端，用于根据用户问题生成 SQL
     * @param dataSource 业务数据库数据源，用于执行只读 SQL
     */
    public EvaSqlDatabaseContentRetriever(ChatClient chatClient, DataSource dataSource) {
        this(chatClient, dataSource, DEFAULT_MAX_ROWS);
    }

    /**
     * 构造 Text2SQL 数据库检索器。
     *
     * @param chatClient 大模型客户端，用于根据用户问题生成 SQL
     * @param dataSource 业务数据库数据源，用于执行只读 SQL
     * @param maxRows    单次查询最大返回行数
     */
    public EvaSqlDatabaseContentRetriever(ChatClient chatClient, DataSource dataSource, int maxRows) {
        this.chatClient = chatClient;
        this.jdbcTemplate = dataSource == null ? null : new JdbcTemplate(dataSource);
        this.maxRows = maxRows <= 0 ? DEFAULT_MAX_ROWS : maxRows;
        this.textToSqlPrompt = loadResource(TEXT_TO_SQL_PROMPT_PATH);
        this.databaseStructure = loadResource(TABLE_STRUCTURE_PATH);
        this.allowedTables = parseAllowedTables(databaseStructure);
    }

    /**
     * 根据 RAG Query 执行结构化数据库检索。
     *
     * <p>返回的 Document 会包含用户问题、最终执行 SQL、结果行数和 Markdown 表格结果，
     * 后续回答模型可以基于该上下文生成自然语言答案。</p>
     *
     * @param query RAG 查询对象
     * @return 数据库查询结果文档；无法生成安全 SQL 时返回空列表
     */
    @Override
    public List<Document> retrieve(Query query) {
        String question = query == null ? "" : query.text();
        log.info("Text2SQL retrieve query: {}", question);

        if (!StringUtils.hasText(question)) {
            return List.of();
        }
        if (chatClient == null || jdbcTemplate == null) {
            log.warn("Text2SQL Retriever 未注入 ChatClient 或 DataSource，跳过数据库检索");
            return List.of();
        }

        try {
            String generatedSql = generateSql(question);
            String executableSql = prepareExecutableSql(generatedSql);
            if (!StringUtils.hasText(executableSql)) {
                log.info("Text2SQL 未生成可执行 SQL, question={}, generatedSql={}", question, generatedSql);
                return List.of();
            }

            List<Map<String, Object>> rows = jdbcTemplate.queryForList(executableSql);
            return List.of(buildResultDocument(question, executableSql, rows));
        } catch (Exception e) {
            log.error("Text2SQL 数据库检索失败, question={}", question, e);
            return List.of(buildErrorDocument(question, e));
        }
    }

    /**
     * 调用大模型生成 SQL。
     *
     * <p>系统 Prompt 中注入的是资源文件提供的表结构，而不是线上全库结构，
     * 这样可以把模型可查询范围收敛到安全评测助手当前允许暴露的资产表。</p>
     *
     * @param question 用户问题
     * @return 模型返回的原始 SQL 文本
     */
    private String generateSql(String question) {
        String systemPrompt = textToSqlPrompt.replace("{DATABASE_STRUCTURE}", databaseStructure);
        String sql = chatClient.prompt()
                .system(systemPrompt)
                .user(question)
                .call()
                .content();
        log.info("Text2SQL generated sql: {}", sql);
        return sql;
    }

    /**
     * 将模型生成 SQL 标准化、校验并补充行数限制。
     *
     * @param generatedSql 模型生成的原始 SQL
     * @return 可以执行的 SQL；不安全或不可用时返回空字符串
     */
    private String prepareExecutableSql(String generatedSql) {
        String sql = normalizeSql(generatedSql);
        if (!StringUtils.hasText(sql) || "\"\"".equals(sql) || "''".equals(sql)) {
            return "";
        }
        if (!isReadOnlySql(sql)) {
            log.warn("Text2SQL 生成了非只读 SQL，已拒绝执行: {}", sql);
            return "";
        }
        if (hasStatementSeparator(sql)) {
            log.warn("Text2SQL 生成了多语句 SQL，已拒绝执行: {}", sql);
            return "";
        }
        if (!usesOnlyAllowedTables(sql)) {
            log.warn("Text2SQL 访问了未授权表，已拒绝执行: {}, allowedTables={}", sql, allowedTables);
            return "";
        }
        return applyLimit(sql);
    }

    /**
     * 清理模型输出中的 Markdown 代码块、空白和末尾分号。
     *
     * @param generatedSql 模型原始输出
     * @return 标准化后的 SQL
     */
    private String normalizeSql(String generatedSql) {
        if (generatedSql == null) {
            return "";
        }
        String sql = generatedSql.strip();
        if (sql.startsWith("```")) {
            sql = sql.replaceFirst("(?is)^```\\s*sql\\s*", "");
            sql = sql.replaceFirst("(?is)^```\\s*", "");
            sql = sql.replaceFirst("(?is)\\s*```\\s*$", "");
        }
        sql = sql.strip();
        while (sql.endsWith(";")) {
            sql = sql.substring(0, sql.length() - 1).stripTrailing();
        }
        return sql;
    }

    /**
     * 判断 SQL 是否为允许的只读查询。
     *
     * @param sql 标准化后的 SQL
     * @return true 表示可以继续执行后续安全校验
     */
    private boolean isReadOnlySql(String sql) {
        String lowerSql = sql.stripLeading().toLowerCase(Locale.ROOT);
        if (!(lowerSql.startsWith("select ") || lowerSql.startsWith("with "))) {
            return false;
        }
        return !FORBIDDEN_SQL_PATTERN.matcher(sql).find();
    }

    /**
     * 判断 SQL 中是否存在多语句分隔符。
     *
     * <p>这里会忽略字符串字面量和反引号字段名中的分号，只拦截 SQL 语句层面的分号。</p>
     *
     * @param sql 标准化后的 SQL
     * @return true 表示存在多语句风险
     */
    private boolean hasStatementSeparator(String sql) {
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        boolean inBacktick = false;

        for (int i = 0; i < sql.length(); i++) {
            char ch = sql.charAt(i);
            char prev = i > 0 ? sql.charAt(i - 1) : '\0';

            if (ch == '\'' && !inDoubleQuote && !inBacktick && prev != '\\') {
                inSingleQuote = !inSingleQuote;
            } else if (ch == '"' && !inSingleQuote && !inBacktick && prev != '\\') {
                inDoubleQuote = !inDoubleQuote;
            } else if (ch == '`' && !inSingleQuote && !inDoubleQuote) {
                inBacktick = !inBacktick;
            } else if (ch == ';' && !inSingleQuote && !inDoubleQuote && !inBacktick) {
                return true;
            }
        }
        return false;
    }

    /**
     * 校验 SQL 引用的表是否全部来自白名单。
     *
     * @param sql 标准化后的 SQL
     * @return true 表示 SQL 未访问白名单之外的表
     */
    private boolean usesOnlyAllowedTables(String sql) {
        Set<String> referencedTables = new LinkedHashSet<>();
        Matcher matcher = TABLE_REFERENCE_PATTERN.matcher(sql);
        while (matcher.find()) {
            referencedTables.add(matcher.group(1).toLowerCase(Locale.ROOT));
        }
        if (referencedTables.isEmpty()) {
            return false;
        }
        return allowedTables.containsAll(referencedTables);
    }

    /**
     * 如果 SQL 未显式限制行数，则追加默认 LIMIT。
     *
     * @param sql 已通过安全校验的 SQL
     * @return 带行数限制的 SQL
     */
    private String applyLimit(String sql) {
        if (LIMIT_PATTERN.matcher(sql).find()) {
            return sql;
        }
        return sql + " LIMIT " + maxRows;
    }

    /**
     * 构建数据库查询结果 Document。
     *
     * @param question 用户问题
     * @param sql      最终执行 SQL
     * @param rows     查询结果
     * @return 可注入 RAG 上下文的文档
     */
    private Document buildResultDocument(String question, String sql, List<Map<String, Object>> rows) {
        String text = """
                结构化数据库查询结果

                用户问题：
                %s

                执行 SQL：
                %s

                结果行数：%d

                查询结果：
                %s
                """.formatted(question, sql, rows.size(), formatRows(rows));

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("retrieval_source", "relational_db");
        metadata.put("sql", sql);
        metadata.put("row_count", rows.size());
        metadata.put("allowed_tables", allowedTables);

        return Document.builder()
                .id("relational_db:" + Integer.toHexString(sql.hashCode()))
                .text(text)
                .metadata(metadata)
                .build();
    }

    /**
     * 构建异常兜底 Document。
     *
     * <p>返回错误上下文可以让最终回答明确说明结构化检索失败，
     * 而不是在空上下文下编造数据库结果。</p>
     *
     * @param question 用户问题
     * @param e        异常信息
     * @return 错误上下文文档
     */
    private Document buildErrorDocument(String question, Exception e) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("retrieval_source", "relational_db");
        metadata.put("error", e.getClass().getSimpleName());

        return Document.builder()
                .id("relational_db:error:" + Integer.toHexString(question.hashCode()))
                .text("""
                        结构化数据库查询失败。

                        用户问题：
                        %s

                        失败原因：
                        %s
                        """.formatted(question, e.getMessage()))
                .metadata(metadata)
                .build();
    }

    /**
     * 将数据库结果格式化为 Markdown 表格。
     *
     * @param rows 查询结果
     * @return Markdown 表格或空结果提示
     */
    private String formatRows(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return "未查询到匹配数据。";
        }

        List<String> columns = new ArrayList<>(rows.getFirst().keySet());
        StringBuilder builder = new StringBuilder();
        builder.append("| ").append(String.join(" | ", columns)).append(" |\n");
        builder.append("| ").append(String.join(" | ", columns.stream().map(column -> "---").toList())).append(" |\n");

        for (Map<String, Object> row : rows) {
            List<String> values = columns.stream()
                    .map(column -> formatCell(row.get(column)))
                    .toList();
            builder.append("| ").append(String.join(" | ", values)).append(" |\n");
        }
        return builder.toString();
    }

    /**
     * 格式化单元格，避免过长文本或换行破坏 RAG 上下文表格。
     *
     * @param value 原始单元格值
     * @return 可安全放入 Markdown 表格的文本
     */
    private String formatCell(Object value) {
        if (value == null) {
            return "";
        }
        String text = String.valueOf(value)
                .replace("\r", " ")
                .replace("\n", " ")
                .replace("|", "\\|")
                .strip();
        if (text.length() > MAX_CELL_TEXT_LENGTH) {
            return text.substring(0, MAX_CELL_TEXT_LENGTH) + "...";
        }
        return text;
    }

    /**
     * 从建表 SQL 中解析允许访问的表名。
     *
     * @param ddl 建表语句
     * @return 表名白名单
     */
    private Set<String> parseAllowedTables(String ddl) {
        Set<String> tables = new LinkedHashSet<>();
        Matcher matcher = CREATE_TABLE_PATTERN.matcher(ddl);
        while (matcher.find()) {
            tables.add(matcher.group(1).toLowerCase(Locale.ROOT));
        }
        return tables;
    }

    /**
     * 加载 classpath 资源文件。
     *
     * @param location 资源路径
     * @return 文件内容
     */
    private String loadResource(String location) {
        try {
            Resource resource = new PathMatchingResourcePatternResolver().getResource(location);
            return FileCopyUtils.copyToString(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("Text2SQL 资源文件加载失败: " + location, e);
        }
    }
}
