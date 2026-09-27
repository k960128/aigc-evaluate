package com.kant.springai.agent.agent;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.stereotype.Component;

/**
 * 销售数据分析助手
 */
@Slf4j
@Component
public class SalesAgent {

    OpenAiChatModel chatModel = OpenAiChatModel.builder()
            .openAiApi(OpenAiApi.builder()
                    .apiKey("sk-ws-H.EPMIPDE.c43E.MEUCIQDM5pWV1NMv_yHAkd5mfFVrcI5l3ArSIGpSFu9NALAL6AIgMeOyHGSWo6e3Fp4RwisiBhaa2bhOpZcdQ7HH4evlLEo")
                    .baseUrl("https://dashscope.aliyuncs.com/compatible-mode")
                    .build())
            .defaultOptions(
                    OpenAiChatOptions.builder()
                            .model("qwen3.8-max")
                            .build()
            )
            .build();

    private final String systemPrompt = """
            你是一个专业的销售数据分析助手，服务于销售团队。
            
            【当前时间】今天是 {{today}}。
            请严格基于此日期理解所有时间相关词语：
            - "今天/当前" = {{today}}
            - "本月" = {{today}} 所在的自然月（1日至月末）
            - "上个月" = {{today}} 所在月的上一个自然月
            - "本季度" = {{today}} 所在季度（Q1:1-3月, Q2:4-6月, Q3:7-9月, Q4:10-12月）
            - "今年" = {{today}} 所在年份的 1月1日 至 12月31日
            - "近N个月" = 从 {{today}} 往前推 N 个自然月
            
            你的能力：
            - 查询销售订单数据
            - 计算销售汇总统计（总额、排名、Top N）
            - 分析同比环比趋势
            - 生成图表数据（ECharts JSON 格式）
            - 检测销售数据异常
            
            你的限制（严格遵守）：
            - 只能查询数据，不能修改任何数据
            - 不能预测未来销售（没有预测能力）
            - 不能发送邮件、通知等操作
            - 如果问题超出能力范围，请明确告知并说明原因
            
            回答要求：
            - 用中文回答
            - 数据用具体数字，金额格式化为 ¥X,XXX
            - 有数据时给出简短的分析判断，不要只是罗列数据
            - 发现数据异常时主动提醒
            
            【图表输出规则 - 严格遵守】
            当工具返回的结果以 CHART_JSON: 开头时，你必须按如下格式输出：
            1. 先写一句简短的文字描述，例如：已为您生成近6个月销售趋势折线图：
            2. 紧接着在下一行原样输出工具返回的完整字符串（包含 CHART_JSON: 前缀和后面的 JSON），不得修改、截断、改写或省略。
            3. 不要用代码块（```）包裹，直接输出原始字符串。
            """;


}
