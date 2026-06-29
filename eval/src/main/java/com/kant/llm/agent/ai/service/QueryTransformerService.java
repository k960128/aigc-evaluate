package com.kant.llm.agent.ai.service;

import com.kant.llm.agent.chat.service.ChatMessageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

/**
 * 问题重写服务
 */
@Slf4j
@Service
public class QueryTransformerService {

    private final ChatClient chatClient;
    private final ChatMessageService chatMessageService;

    // 问题重写提示词
    private final String QUERY_TRANSFORMER_PROMPT = "你是一个汽车智能客服助手，你的职责范围是汽车相关的咨询场景，包括购车咨询、车型信息、保养维修、保险年检、售后服务等。你需要对用户的问题进行改写，使得改写后的问题在查询向量数据库/关系型数据库/图数据库时有更好的结果，并删除任何无关信息，确保查询简洁明了、具体明确。下面有一些改写的策略。\n" +
            "            \n" +
            "            1、简洁改写。问题可能比较长，包含了一些无意义的语气词、修饰词或者重复的词语等。尤其是问题在询问车型配置、价格政策时，且包含一些无意义的日期、编号等修饰词。改写规则：删除无意义的词语使其更适合搜索引擎检索，疑问句要转成陈述句。\n" +
            "            2、抽象概念改写。前提：用户的问题一定在询问汽车相关的问题，且是一些比较具体的细节问题，比如\"我的车每次踩刹车的时候都有吱吱吱的声音很吵怎么办\"。需要改写成类似\"车辆刹车异响故障排查\"，将具体的问题转化为更基础、更简洁、更抽象的问题。\n" +
            "            3、错别字改写。用户的问题包含了错别字或者是一些常见的汽车术语用户打成了对应的拼音。大小写不一样不属于错别字。错别字需要给出纠正结果。\n" +
            "            4、车型信息提取。如果用户提到了具体的车型信息（品牌、型号、年款等），需要将其标准化提取。比如\"特斯拉毛豆3\"改写为\"Tesla Model 3\"，\"比亚迪汉\"保持不变。\n" +
            "            \n" +
            "            上面是4种改写策略，需要逐一使用最终给出一个统一的改写结果。直接输出改写后的结果，不需要输出思考过程及额外的多余内容。如果不需要改写，则直接输出原问题即可。\n" +
            "            \n" +
            "            下面是几个示例：\n" +
            "            \n" +
            "            Input：我如果想买一辆特斯拉Model 3的话，大概需要多少钱啊\n" +
            "            Output：Tesla Model 3官方指导价\n" +
            "            \n" +
            "            Input：我的车该保养了，多久保养一次？\n" +
            "            Output：车辆保养周期规定\n" +
            "            \n" +
            "            Input：我的比亚迪汉刹车有点异响是怎么回事\n" +
            "            Output：车辆刹车异响故障排查\n" +
            "            \n" +
            "            Input：毛豆Y续航多少\n" +
            "            Output：Tesla Model Y续航里程\n" +
            "            \n" +
            "            Input：保险什么时候到期？\n" +
            "            Output：车辆保险到期查询\n" +
            "            \n" +
            "            Input：年检怎么办理？\n" +
            "            Output：车辆年检办理流程\n" +
            "            \n" +
            "            Input：我想了解一下你们那款新出的电动车的配置\n" +
            "            Output：新款电动车车型配置参数\n" +
            "            \n" +
            "            用户的原始查询：{QUERY}\n" +
            "            ";

    public QueryTransformerService(ChatClient chatClient,
                                   ChatMessageService chatMessageService) {
        this.chatClient = chatClient;
        this.chatMessageService = chatMessageService;
    }


    /**
     * 问题重写
     * @param query 原始问题
     * @param assistantMsgId 助手消息ID
     * @param progressCallback 进度回调
     * @return 改写结果
     */
    public Collection<String> transformer(String query,
                                          String assistantMsgId,
                                          Consumer<String> progressCallback) {
        // 发送进度开始改写问题
        if (progressCallback != null) {
            progressCallback.accept("[PROGRESS]:正在优化您的问题...");
        }

        log.info("开始问题改写，原始问题：{}", query);
        String response = chatClient.prompt(createPrompt(query)).call().chatResponse().getResult().getOutput().getText();
        log.info("问题改写完成, 改写结果: {}", response);

        String newQuery = "我的问题是：" + response + ", 我的用户Id是: 123321" + ", 现在是：" + LocalDateTime.now();

        // 异步回写改写结果到 chat_message
        if (assistantMsgId != null) {
            Thread.ofVirtual().name("query-transform-" + assistantMsgId).start(() -> {
                try {
                    chatMessageService.updateTransformContent(assistantMsgId, newQuery);
                    log.info("改写结果已回写: assistantMsgId={}, transformContent={}", assistantMsgId, response);
                } catch (Exception e) {
                    log.warn("改写结果回写失败: assistantMsgId={}", assistantMsgId, e);
                }
            });
        }
        return List.of(response, query);
    }


    protected Prompt createPrompt(String query) {
        PromptTemplate promptTemplate = new PromptTemplate(QUERY_TRANSFORMER_PROMPT);
        promptTemplate.add("QUERY", query);
        return promptTemplate.create();
    }

}
