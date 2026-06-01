package com.kant.llm.rag;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;

@Slf4j
public class RagQueryRouterTest extends AbstractRagQuery {


    private static final String QUERY_ROUTE_PROMPT = """
            你需要判断用户的查询问题适合使用哪种数据库进行检索。
            如果是语义相似性搜索、文档检索、内容推荐类问题，回答'VECTOR'
            如果是关系查询、知识图谱、实体关联类问题，回答'GRAPH'
            如果是结构化数据查询、统计分析、精确匹配类问题，回答'RELATIONAL'
            如果无法确定，请回答'VECTOR'
            只回答VECTOR、GRAPH或RELATIONAL，不要其他内容。
            
            用户问题：
            {QUESTION}
            """;

    @Test
    void testRagQueryRouter() {
        ChatClient chatClient = getChatClient();
        PromptTemplate promptTemplate = new PromptTemplate(QUERY_ROUTE_PROMPT);

        String userQuestion = "查询数据表A和数据表B的血缘关系";
//        String userQuestion = "A部门年龄大于30的员工信息";
//        String userQuestion = "A部门评级是如何计算的？";
        promptTemplate.add("QUESTION", userQuestion);

        String checkType = chatClient.prompt(promptTemplate.create()).call().chatResponse().getResult().getOutput().getText();
        log.info("checkType: {}", checkType);

        switch (checkType.trim().toUpperCase()) {
            case "GRAPH":
                log.info(GraphQuery(userQuestion));
                break;
            case "RELATIONAL":
                log.info(RelationalQuery(userQuestion));
                break;
            case "VECTOR":
            default:
                log.info(vectorQuery(userQuestion));
                break;
        }
    }


    /**
     * 图数据库查询
     * @param query
     * @return
     */
    public String GraphQuery(String query) {
        return "图数据库搜索结果: 基于关系图谱，找到与'" + query + "'相关的实体关系和路径。" +
                "这里模拟返回了知识图谱的实体关联结果，实际应用中会连接到Neo4j、ArangoDB或Amazon Neptune等图数据库。";
    }

    /**
     * 关系型数据库查询
     * @param query
     * @return
     */
    public String RelationalQuery(String query) {
        return "关系型数据库搜索结果: 基于结构化查询，找到与'" + query + "'匹配的数据记录。" +
                "这里模拟返回了SQL查询结果，实际应用中会连接到MySQL、PostgreSQL或Oracle等关系型数据库进行精确查询和统计分析。";
    }

    /**
     * 向量数据库查询
     * @param query
     * @return
     */
    public String vectorQuery(String query) {
        return "向量数据库搜索结果: 基于语义相似性，找到与'" + query + "'相关的文档片段。" +
                "这里模拟返回了相关的嵌入向量匹配结果，实际应用中会连接到真实的向量数据库如Chroma、Milvus、Faiss等。";
    }
}
