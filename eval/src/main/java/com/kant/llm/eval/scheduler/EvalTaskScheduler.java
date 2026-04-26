package com.kant.llm.eval.scheduler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 评测定时任务
 */
@Slf4j
@Component
public class EvalTaskScheduler {


    /**
     * 定时任务 每秒执行一次任务
     */
//    @Scheduled(fixedDelay = 1000)
    public void schedule() {
        log.info("schedule...........................");
    }
}
