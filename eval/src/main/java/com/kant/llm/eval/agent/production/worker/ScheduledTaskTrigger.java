package com.kant.llm.eval.agent.production.worker;
import com.kant.llm.eval.agent.production.service.SampleProductionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.Instant;
/** 持久化每日计划触发器；默认关闭；同槽位批次唯一性由事务和数据库保证。 */
@Slf4j @Component
@ConditionalOnProperty(name="eval.agent.sample-factory.scheduler-enabled",havingValue="true")
public class ScheduledTaskTrigger {
    private final SampleProductionService production;
    public ScheduledTaskTrigger(SampleProductionService production) { this.production=production; }
    // @Scheduled(fixedDelayString="${eval.agent.sample-factory.schedule-interval-ms:5000}")
    public void trigger() {
        Instant at=Instant.now();
        for(var task:production.due(at)) try { production.triggerScheduled(task.getTaskId(),at); }
        catch(RuntimeException ex) { log.warn("D05计划触发失败，taskId={}, errorType={}",task.getTaskId(),ex.getClass().getSimpleName()); }
    }
}
