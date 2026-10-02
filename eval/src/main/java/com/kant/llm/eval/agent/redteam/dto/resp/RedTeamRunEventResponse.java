package com.kant.llm.eval.agent.redteam.dto.resp;
import java.time.Instant;
/**
 * 只携带安全元数据的持久化事件，不广播输入或响应正文。
 * @param eventId 全局数据库事件ID，仅用作本运行订阅游标和去重
 * @param sequence 本运行单调递增资源版本，可能有间隔
 * @param runId 所属运行
 * @param taskId 所属任务
 * @param eventType 事件类型
 * @param status 当时状态
 * @param subjectRef 涉及对象引用；当前为运行ID
 * @param summary 安全摘要
 * @param occurredAt 持久化时间
 * @param schemaVersion 当前事件契约版本
 */
public record RedTeamRunEventResponse(long eventId,long sequence,String runId,String taskId,String eventType,
        String status,String subjectRef,String summary,Instant occurredAt,String schemaVersion) {}
