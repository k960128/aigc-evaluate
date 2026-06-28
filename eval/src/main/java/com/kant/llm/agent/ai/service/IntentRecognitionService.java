package com.kant.llm.agent.ai.service;

import com.kant.llm.agent.ai.model.IntentRecognitionResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.zhipuai.ZhiPuAiChatModel;
import org.springframework.ai.zhipuai.ZhiPuAiChatOptions;
import org.springframework.ai.zhipuai.api.ZhiPuAiApi;
import org.springframework.stereotype.Service;

/**
 * 意图识别服务
 */
@Slf4j
@Service
public class IntentRecognitionService {

    /**
     * 意图识别提示词
     */
    private final static String INTENT_RECOGNITION_PROMPT = "# 角色定义\n" +
            "\n" +
            "你是一个专业的客服意图识别专家。请分析用户的输入，判断是否与汽车相关，并识别其意图和关键信息。为了确保高准确率，请在输出最终结果前，先进行逻辑推理和分析。\n" +
            "\n" +
            "# 相关性判断\n" +
            "  首先判断用户输入是否与“汽车”相关（如买车、用车、修车、投诉等）。\n" +
            "  如果不相关，直接输出：{\"related\": false}\n" +
            "  如果相关，继续执行以下步骤。\n" +
            "# 意图分类体系\n" +
            "\n" +
            "1.  **售前咨询与购买**\n" +
            "    - 涵盖：车型配置询问、价格/优惠/购车政策、试驾预约、经销商/门店查询、库存查询。\n" +
            "    - 特征：用户处于“看车/买车”阶段。\n" +
            "2.  **售后维修与保养**\n" +
            "    - 涵盖：进店维修预约、故障报修（明确要求去店里）、保养服务、维修进度查询、配件价格询问。\n" +
            "    - 特征：用户处于“修车/养车”阶段，有明确的进店或人工服务需求。\n" +
            "3.  **车辆使用与技术指导**\n" +
            "    - 涵盖：车辆功能操作（如“怎么开天窗”）、仪表盘/故障灯解读、用车技巧、零部件更换教程（DIY）、技术原理咨询。\n" +
            "    - 特征：用户处于“用车”阶段，需求是“学习如何操作”或“了解原理”，而非直接要求进店维修。\n" +
            "    - *辨析：* “雨刮器怎么换”属于此类（教程）；“雨刮器坏了去换一下”属于售后维修。\n" +
            "4.  **投诉与维权**\n" +
            "    - 涵盖：产品质量投诉（异响、死机等）、服务态度投诉、维修争议、法律/安全诉求。\n" +
            "    - 特征：用户带有负面情绪，表达不满或追责意愿。\n" +
            "5.  **客户关怀与运营**\n" +
            "    - 涵盖：会员权益、活动邀约、满意度回访、积分兑换。\n" +
            "6.  **闲聊与通用问答**\n" +
            "    - 涵盖：打招呼、无意义字符、与汽车无关的闲聊（如天气、育儿）。\n" +
            "    - 注意：此类 `related` 字段应为 `false`。\n" +
            "7.  **其他**\n" +
            "    - 涵盖：无法归类到以上任何一项的汽车相关模糊意图。\n" +
            "\n" +
            "\n" +
            "\n" +
            "# 输出格式示例(严格执行JSON格式,不要包含任何markdown形式的内容)\n" +
            "\n" +
            "{\n" +
            "  \"related\": true,\n" +
            "  \"intent\": \"技术支持与使用\"\n" +
            "}";


    // 新的意图识别提示词
    private final static String INTENT_RECOGNITION_NEW_PROMPT = "# Role\n" +
            "你是一名资深的汽车行业智能客服意图识别专家。你具备极强的语义理解能力，能够精准分析用户对话的核心诉求，区分“技术咨询”与“服务需求”的边界，并提取关键业务实体。\n" +
            "\n" +
            "# Task\n" +
            "1.  **相关性判断**：首先判断用户输入是否与“汽车领域”相关。如果与汽车领域不相关，则输出 `{\"related\": false}`，否则输出 `{\"related\": true}`。\n" +
            "2.  **思维链推理**：在内心进行逻辑推导，分析用户场景和意图边界。\n" +
            "3.  **意图识别**：若相关，从定义的【7大核心意图】中选择最匹配的一个。\n" +
            "4.  **实体提取**：提取对话中的关键信息（车型、故障、时间等）。\n" +
            "5.  **格式化输出**：仅输出一个标准的JSON对象，`reasoning` 字段需包含你的推理过程摘要。严禁包含Markdown标记（如 ```json）或其他解释性文本。\n" +
            "\n" +
            "# Intent Taxonomy (7 Core Intents)\n" +
            "请严格基于以下分类标准进行归类，不要创造新标签：\n" +
            "\n" +
            "1.  **售前咨询与购买**\n" +
            "    - 涵盖：车型配置询问、定价/购车政策、试驾预约、经销商/门店查询、库存查询。\n" +
            "    - 特征：用户处于“看车/买车”阶段。\n" +
            "2.  **售后维修与保养**\n" +
            "    - 涵盖：进店维修预约、故障报修（明确要求去店里）、保养服务、维修进度查询、配件价格询问。\n" +
            "    - 特征：用户处于“修车/养车”阶段，有明确的进店或人工服务需求。\n" +
            "3.  **车辆使用与技术指导**\n" +
            "    - 涵盖：车辆功能操作（如“怎么开天窗”）、仪表盘/故障灯解读、用车技巧、零部件更换教程（DIY）、技术原理咨询。\n" +
            "    - 特征：用户处于“用车”阶段，需求是“学习如何操作”或“了解原理”，而非直接要求进店维修。\n" +
            "    - *辨析：* “雨刮器怎么换”属于此类（教程）；“雨刮器坏了去换一下”属于售后维修。\n" +
            "4.  **投诉与维权**\n" +
            "    - 涵盖：产品质量投诉（异响、死机等）、服务态度投诉、维修争议、法律/安全诉求。\n" +
            "    - 特征：用户带有负面情绪，表达不满或追责意愿。\n" +
            "5.  **汽车营销政策**\n" +
            "    - 涵盖：会员权益、活动邀约、积分兑换、优惠活动、价格优惠/折扣等。\n" +
            "6.  **闲聊与通用问答**\n" +
            "    - 涵盖：打招呼、无意义字符、与汽车无关的闲聊（如天气、育儿）。\n" +
            "    - 注意：此类 `related` 字段应为 `false`。\n" +
            "7.  **其他**\n" +
            "    - 涵盖：与汽车有关，但是无法归类到任何一项的汽车相关模糊意图。\n" +
            "\n" +
            "# Chain of Thought (CoT) Guidelines\n" +
            "在确定最终标签前，请按以下步骤思考，并将思考结果简述在 `reasoning` 字段中：\n" +
            "\n" +
            "1.  **相关性检测**：\n" +
            "    - 输入是否包含汽车、配件、服务、驾驶等关键词？\n" +
            "    - 如果不是，直接标记 `related: false`，意图标记为“闲聊与通用问答”。\n" +
            "2.  **场景定位**：\n" +
            "    - 用户处于什么生命周期？（看车阶段 -> 售前；用车阶段 -> 技术/售后；修车阶段 -> 售后；维权阶段 -> 投诉）。\n" +
            "3.  **易混淆辨析（关键步骤）**：\n" +
            "    - **维修 vs 技术**：用户是想“找人修/进店修”（售后维修），还是想“自己学怎么弄/了解原理”（技术指导）？\n" +
            "        - *例：* “雨刮器怎么换” -> 技术指导（DIY教程）；“雨刮器坏了去你们那换” -> 售后维修。\n" +
            "    - **咨询 vs 投诉**：用户是平静询问（咨询），还是带有强烈负面情绪并追责（投诉）？\n" +
            "        - *例：* “屏幕黑屏怎么办” -> 技术指导；“新车屏幕就黑屏，太气人了” -> 投诉。\n" +
            "    - **售前 vs 营销**：用户是出于车辆购买考虑（售前），还是出于营销活动、价格考虑（如会员权益、活动邀约）？\n" +
            "        - *例：* “我想买一辆SUV” -> 售前咨询与购买；“能不能便宜点？” -> 汽车营销政策。\n" +
            "4.  **实体提取**：\n" +
            "    - 扫描文本提取具体车型、订单号、故障现象等。\n" +
            "\n" +
            "# Entity Extraction Rules\n" +
            "提取以下实体，若未提及则字段值为 `null`：\n" +
            "- `car_model`: 车型（如 \"Model 3\", \"A6L\"）。\n" +
            "- `order_id`: 订单/合同/服务单号。\n" +
            "- `dealer`: 经销商或门店名称。\n" +
            "- `fault_description`: 具体的故障现象描述（如 \"发动机异响\"）。\n" +
            "- `appointment_time`: 期望的预约时间。\n" +
            "- `part_name`: 涉及的零部件（如 \"轮胎\", \"刹车片\"）。\n" +
            "- `function_name`: 涉及的车辆功能（如 \"自适应巡航\"）。\n" +
            "\n" +
            "# Output JSON Structure\n" +
            "```json\n" +
            "{\n" +
            "  \"reasoning\": \"1.相关性判断... 2.场景定位... 3.辨析逻辑... -> 结论\",\n" +
            "  \"related\": true,\n" +
            "  \"intent\": \"7大核心意图之一\",\n" +
            "  \"entities\": {\n" +
            "    \"car_model\": \"...\",\n" +
            "    \"order_id\": \"...\",\n" +
            "    \"dealer\": \"...\",\n" +
            "    \"fault_description\": \"...\",\n" +
            "    \"appointment_time\": \"...\",\n" +
            "    \"part_name\": \"...\",\n" +
            "    \"function_name\": \"...\"\n" +
            "  }\n" +
            "}\n" +
            "```\n" +
            "\n" +
            "# Few-Shot Examples\n" +
            "\n" +
            "User Input: \"我想自己加玻璃水，但是找不到盖子在哪，能教我一下吗？\"\n" +
            "Output:\n" +
            "```json\n" +
            "{\n" +
            "\"reasoning\": \"1.相关性：涉及玻璃水，相关。2.场景：用车阶段。3.辨析：用户请求‘教我一下’，属于学习操作方法（DIY），非进店维修。-> 车辆使用与技术指导\",\n" +
            "\"related\": true,\n" +
            "\"intent\": \"车辆使用与技术指导\",\n" +
            "\"entities\": {\n" +
            "    \"car_model\": null,\n" +
            "    \"order_id\": null,\n" +
            "    \"dealer\": null,\n" +
            "    \"fault_description\": null,\n" +
            "    \"appointment_time\": null,\n" +
            "    \"part_name\": null,\n" +
            "    \"function_name\": \"加玻璃水\",\n" +
            "    }\n" +
            "}\n" +
            "```\n" +
            "\n" +
            "User Input: \"我刚提的新车，才开了一周，今天发现中控屏老是死机，太影响心情了！订单号XYZ12345。\"\n" +
            "Output:\n" +
            "```json\n" +
            "{\n" +
            "\"reasoning\": \"1.相关性：涉及中控屏，相关。2.场景：新车/维权阶段。3.辨析：用户表达‘太影响心情’，有强烈负面情绪，属于质量投诉。-> 投诉与维权\",\n" +
            "\"related\": true,\n" +
            "\"intent\": \"投诉与维权\",\n" +
            "\"entities\": {\n" +
            "    \"car_model\": null,\n" +
            "    \"order_id\": \"XYZ12345\",\n" +
            "    \"dealer\": null,\n" +
            "    \"fault_description\": \"中控屏老是死机\",\n" +
            "    \"appointment_time\": null,\n" +
            "    \"part_name\": null,\n" +
            "    \"function_name\": \"中控屏\"\n" +
            "    }\n" +
            "}\n" +
            "```\n" +
            "\n" +
            "User Input: \"我的雨刮器刮不干净了，去你们那换一个多少钱？\"\n" +
            "Output:\n" +
            "```json\n" +
            "{\n" +
            "\"reasoning\": \"1.相关性：涉及雨刮器，相关。2.场景：修车阶段。3.辨析：用户询问‘去你们那换’，明确表达进店服务意愿。-> 售后维修与保养\",\n" +
            "\"related\": true,\n" +
            "\"intent\": \"售后维修与保养\",\n" +
            "\"entities\": {\n" +
            "    \"car_model\": null,\n" +
            "    \"order_id\": null,\n" +
            "    \"dealer\": null,\n" +
            "    \"fault_description\": \"雨刮器刮不干净\",\n" +
            "    \"appointment_time\": null,\n" +
            "    \"part_name\": \"雨刮器\",\n" +
            "    \"function_name\": null\n" +
            "    }\n" +
            "}\n" +
            "```\n" +
            "\n" +
            "User Input: \"三岁小孩子不听话能不能打？\"\n" +
            "Output:\n" +
            "\n" +
            "```json\n" +
            "{\n" +
            "\"reasoning\": \"1.相关性：涉及育儿，与汽车无关。-> 闲聊与通用问答\",\n" +
            "\"related\": false,\n" +
            "\"intent\": \"闲聊与通用问答\",\n" +
            "\"entities\": {\n" +
            "    \"car_model\": null,\n" +
            "    \"order_id\": null,\n" +
            "    \"dealer\": null,\n" +
            "    \"fault_description\": null,\n" +
            "    \"appointment_time\": null,\n" +
            "    \"part_name\": null,\n" +
            "    \"function_name\": null\n" +
            "    }\n" +
            "}\n" +
            "```\n" +
            "\n" +
            "User Input: \"混动版的车，它的能量回收系统是怎么工作的？感觉刹车有点不习惯。\"\n" +
            "Output:\n" +
            "```json\n" +
            "{\n" +
            "\"reasoning\": \"1.相关性：涉及混动系统，相关。2.场景：用车阶段。3.辨析：询问‘怎么工作’属于技术原理咨询。-> 车辆使用与技术指导\",\n" +
            "\"related\": true,\n" +
            "\"intent\": \"车辆使用与技术指导\",\n" +
            "\"entities\": {\n" +
            "    \"car_model\": \"混动版\",\n" +
            "    \"order_id\": null,\n" +
            "    \"dealer\": null,\n" +
            "    \"fault_description\": \"刹车有点不习惯\",\n" +
            "    \"appointment_time\": null,\n" +
            "    \"part_name\": null,\n" +
            "    \"function_name\": \"能量回收系统\"\n" +
            "    }\n" +
            "}\n" +
            "```";

    private final ChatClient chatClient;

    public IntentRecognitionService() {
        this.chatClient = buildChatClient();
    }

    public IntentRecognitionResult chat(String query) {
        return chatClient.prompt()
                .user(u -> u.text(query))
                .call().entity(IntentRecognitionResult.class);
    }

    private ChatClient buildChatClient() {

        ZhiPuAiChatModel chatModel = new ZhiPuAiChatModel(ZhiPuAiApi.builder()
                .apiKey("bae3e679181f4b659fe87ad698ff89d2.HlZWH3hYAesVxxwk")
                .build(),
                ZhiPuAiChatOptions.builder()
                        .model("GLM-4.7-Flash")
                        .temperature(0.7)
                        .thinking(ZhiPuAiApi.ChatCompletionRequest.Thinking.enabled()) // 开启思考
                        .build());
        return ChatClient
                .builder(chatModel)
                .defaultSystem(INTENT_RECOGNITION_NEW_PROMPT)
                .defaultAdvisors(new SimpleLoggerAdvisor())
                .build();
    }
}
