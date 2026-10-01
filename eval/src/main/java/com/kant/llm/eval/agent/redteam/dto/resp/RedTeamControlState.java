package com.kant.llm.eval.agent.redteam.dto.resp;
/**
 * 命令与事件订阅前读取的一致控制快照。
 * @param runId 当前运行
 * @param status 数据库权威状态
 * @param version 下一次命令填写的expectedVersion；心跳续租不改变此版本
 * @param eventCursor 快照时最后一个事件ID；从该游标之后续订，0代表尚无事件
 * @param stopRequested 已保存的停止意图
 */
public record RedTeamControlState(String runId,String status,long version,long eventCursor,boolean stopRequested) {}
