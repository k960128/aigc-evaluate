package com.kant.llm.eval.agent.redteam.dto.resp;
import java.time.Instant;
/**
 * 命令受理回执，不保证运行已经到达所请求的状态。
 * @param commandId 持久化命令标识
 * @param runId 所属运行
 * @param type 控制意图
 * @param acceptedVersion 受理时产生的运行版本；幂等重放返回原值
 * @param acceptedAt 首次受理时间
 */
public record RedTeamCommandReceipt(String commandId,String runId,String type,long acceptedVersion,Instant acceptedAt) {}
