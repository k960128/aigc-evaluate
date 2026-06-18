package com.kant.llm.eval.engine;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定时上报本机 AC 节点状态。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RiskVocabularyAcNodeHeartbeat {

    private final L1InterceptionEngine l1InterceptionEngine;

    @Scheduled(fixedDelay = 20_000L, initialDelay = 5_000L)
    public void heartbeat() {
        try {
            l1InterceptionEngine.heartbeat();
        } catch (Exception ex) {
            log.warn("\u4e0a\u62a5 AC \u8282\u70b9\u5fc3\u8df3\u5931\u8d25", ex);
        }
    }
}
