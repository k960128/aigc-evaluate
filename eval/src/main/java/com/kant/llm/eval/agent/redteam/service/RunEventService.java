package com.kant.llm.eval.agent.redteam.service;

import com.kant.framework.exception.ClientException;
import com.kant.llm.eval.agent.probe.application.ProbePrincipal;
import com.kant.llm.eval.agent.probe.application.ProbePrincipalProvider;
import com.kant.llm.eval.agent.redteam.dto.resp.RedTeamControlState;
import com.kant.llm.eval.agent.redteam.dto.resp.RedTeamRunEventResponse;
import com.kant.llm.eval.agent.redteam.error.AgentRedTeamErrorCode;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

/**
 * D03运行事件查询与SSE续订。持久化事件是事实来源，内存只维护当前展示连接。
 * 连接断开/进程重启不取消运行，客户端带最后事件ID重新连接即可补发。
 */
@Service
public class RunEventService {
    private final AgentRedTeamRecordService records;
    private final ProbePrincipalProvider principals;
    private final Map<String,Subscription> subscriptions=new ConcurrentHashMap<>();
    // 单独的事件线程，慢浏览器连接不能阻塞运行租约心跳和调度线程。
    private final ScheduledExecutorService dispatcher=Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread=new Thread(r,"red-team-events"); thread.setDaemon(true); return thread;
    });
    public RunEventService(AgentRedTeamRecordService records,ProbePrincipalProvider principals) {
        this.records=records; this.principals=principals;
    }
    @PostConstruct
    public void start() { dispatcher.scheduleWithFixedDelay(this::publish,1,1,TimeUnit.SECONDS); }
    @PreDestroy
    public void stop() { dispatcher.shutdownNow(); subscriptions.values().forEach(s -> s.emitter.complete()); subscriptions.clear(); }

    /** 先获取控制快照，再用其eventCursor订阅增量，避免快照和订阅之间漏事件。 */
    public RedTeamControlState state(String runId) {
        var p=principals.currentPrincipal(); return records.controlState(p.tenantId(),p.actorId(),runId);
    }
    /** 分页拉取事件也可用于不支持SSE的客户端。 */
    public List<RedTeamRunEventResponse> history(String runId,long cursor) {
        checkCursor(cursor); var p=principals.currentPrincipal();
        return records.listEvents(p.tenantId(),p.actorId(),runId,cursor);
    }
    /** SSE连接最多存活60秒，超时后由浏览器用Last-Event-ID自动续订。 */
    public synchronized SseEmitter subscribe(String runId,long cursor) {
        checkCursor(cursor); ProbePrincipal p=principals.currentPrincipal();
        RedTeamControlState snapshot=records.controlState(p.tenantId(),p.actorId(),runId);
        if (cursor>snapshot.eventCursor()) throw invalid("事件游标超过当前运行末尾");
        if (subscriptions.size()>=128) throw invalid("事件连接达到当前实例上限");
        SseEmitter emitter=new SseEmitter(60_000L);
        String key=UUID.randomUUID().toString();
        Subscription s=new Subscription(p,runId,emitter,cursor);
        emitter.onCompletion(() -> subscriptions.remove(key));
        emitter.onTimeout(() -> { subscriptions.remove(key); emitter.complete(); });
        emitter.onError(error -> subscriptions.remove(key));
        subscriptions.put(key,s); return emitter;
    }
    /** 每次查询都重做运行归属检查，不把客户端参数当成授权。 */
    private void publish() {
        subscriptions.forEach((key,s) -> {
            try {
                for (var event:records.listEvents(s.principal.tenantId(),s.principal.actorId(),s.runId,s.cursor)) {
                    s.emitter.send(SseEmitter.event().id(Long.toString(event.eventId())).name("run-event").data(event));
                    s.cursor=event.eventId();
                }
            } catch (IOException | RuntimeException ex) {
                subscriptions.remove(key); s.emitter.complete();
            }
        });
    }
    private void checkCursor(long cursor) { if (cursor<0) throw invalid("事件游标不能为负数"); }
    private ClientException invalid(String message) { return new ClientException(message,AgentRedTeamErrorCode.INVALID_TASK_REQUEST); }
    /** 游标只代表已经写给此连接的事件；最终以客户端Last-Event-ID为准。 */
    private static final class Subscription {
        final ProbePrincipal principal; final String runId; final SseEmitter emitter; long cursor;
        Subscription(ProbePrincipal principal,String runId,SseEmitter emitter,long cursor) {
            this.principal=principal; this.runId=runId; this.emitter=emitter; this.cursor=cursor;
        }
    }
}
