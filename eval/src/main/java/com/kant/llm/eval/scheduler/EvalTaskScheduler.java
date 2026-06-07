package com.kant.llm.eval.scheduler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 评测定时任务。
 *
 * <p>评测任务拆分已切换到 RocketMQ 链路，保留该组件用于后续归档类定时任务扩展。</p>
 */
@Slf4j
@Component
public class EvalTaskScheduler {
}
