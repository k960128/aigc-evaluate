package com.kant.llm.eval.agent.production.worker;
import com.kant.llm.eval.agent.production.service.SampleProductionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.Instant;
/**
 * 每日计划触发入口，只负责创建批次，真正生成由SampleProductionWorker完成。
 * scheduler-enabled决定是否创建此Bean；当前定时注解仍被注释，打开配置本身不会启动周期扫描。
 * 同槽位批次唯一性由服务事务和数据库约束保证，不依赖内存定时器只触发一次。
 */
@Slf4j @Component
@ConditionalOnProperty(name="eval.agent.sample-factory.scheduler-enabled",havingValue="true")
public class ScheduledTaskTrigger {
    /** 查询到期计划并在计划行锁内处理调度槽位。 */
    private final SampleProductionService production;
    /** 注入调度受理业务，不在构造时创建批次。 */
    public ScheduledTaskTrigger(SampleProductionService production) { this.production=production; }
    /** 同一轮使用同一个UTC时点；单个计划失败不阻止其他到期计划，日志不含配置或正文。 */
    // @Scheduled(fixedDelayString="${eval.agent.sample-factory.schedule-interval-ms:5000}")
    public void trigger() {
        Instant at=Instant.now();
        for(var task:production.due(at)) try { production.triggerScheduled(task.getTaskId(),at); }
        catch(RuntimeException ex) { log.warn("D05计划触发失败，taskId={}, errorType={}",task.getTaskId(),ex.getClass().getSimpleName()); }
    }
}
