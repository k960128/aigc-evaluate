package com.kant.llm.eval.scheduler;

import com.kant.llm.eval.service.RiskSyncService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class RiskSyncScheduler {

    @Autowired
    private RiskSyncService riskSyncService;

    @Scheduled(cron = "0 0 */2 * * ?")
    public void scheduledSync() {
        log.info("定时任务：开始全量同步风险词汇");
        try {
            riskSyncService.syncAll();
        } catch (Exception e) {
            log.error("定时同步任务失败", e);
        }
    }
}
