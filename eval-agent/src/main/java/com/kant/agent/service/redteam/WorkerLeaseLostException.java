package com.kant.agent.service.redteam;

/** Worker 已失去租约；必须直接退出，不能再写运行失败或覆盖接管者的事实。 */
public class WorkerLeaseLostException extends IllegalStateException {
    public WorkerLeaseLostException() { super("运行Worker租约已失效"); }
}
