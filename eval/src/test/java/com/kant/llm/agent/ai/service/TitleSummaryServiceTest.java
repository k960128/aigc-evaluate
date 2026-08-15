package com.kant.llm.agent.ai.service;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@Slf4j
@SpringBootTest
@ActiveProfiles("test")
public class TitleSummaryServiceTest {

    @Resource
    private TitleSummaryService titleSummaryService;

    @Test
    public void titleSummaryTest(){
        String title = titleSummaryService.generateTitle("我们需要开发一个大模型安全治理平台，现阶段需要根据业务进行中间件的选型。 业务包含，用户发起评测任务，然后通过MQ来进行解耦。 你认为这个功能应该使用rocketmq还是kafka呢。各自有缺点是什么");
        log.info("生成的标题话标题: {}", title);
    }
}
