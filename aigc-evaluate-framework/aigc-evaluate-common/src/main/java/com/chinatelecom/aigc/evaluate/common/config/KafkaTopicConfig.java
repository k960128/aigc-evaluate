package com.chinatelecom.aigc.evaluate.common.config;

import org.springframework.context.annotation.Configuration;

@Configuration
public class KafkaTopicConfig {
    // 主题名称前缀
    private static final String TASK_TOPIC_PREFIX = "ai-evaluation-task-";
    private static final String RESULT_TOPIC_PREFIX = "ai-result-";
    private static final String DEAD_LETTER_TOPIC = "ai-dead-letter";

    /**
     * 根据任务ID和模型ID生成唯一的主题名称
     * @param taskId 任务ID
     * @param modelId 模型ID
     * @return 主题名称
     */
    public static String getTopicName(Long taskId, Long modelId) {
        if (taskId == null || modelId == null) {
            throw new IllegalArgumentException("taskId和modelId不能为空");
        }
        return TASK_TOPIC_PREFIX + modelId + "-" + taskId;
    }

    /**
     * 生成结果主题名称
     * @param taskId 任务ID
     * @return 结果主题名称
     */
    public static String getResultTopicName(Long taskId) {
        if (taskId == null) {
            throw new IllegalArgumentException("taskId不能为空");
        }
        return RESULT_TOPIC_PREFIX + taskId;
    }

    /**
     * 获取死信队列主题名称
     * @return 死信队列主题名称
     */
    public static String getDeadLetterTopic() {
        return DEAD_LETTER_TOPIC;
    }
}
