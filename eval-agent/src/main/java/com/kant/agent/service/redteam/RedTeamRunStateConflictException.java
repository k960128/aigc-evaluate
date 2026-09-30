package com.kant.agent.service.redteam;

/**
 * 自动红队运行启动时的状态竞争异常。
 *
 * <p>该异常只表示任务状态已经被其他请求改变，或当前运行不再满足启动
 * 条件。持久化写入失败仍使用普通运行时异常向上抛出，由应用层映射为
 * 独立的运行记录保存错误，避免把基础设施故障伪装成业务状态冲突。</p>
 */
public final class RedTeamRunStateConflictException extends RuntimeException {

    /**
     * 创建一次运行状态竞争异常。
     *
     * @param message 面向应用层的安全摘要
     */
    public RedTeamRunStateConflictException(String message) {
        super(message);
    }
}
