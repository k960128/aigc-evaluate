package com.kant.llm.eval.agent.redteam.worker;
import com.kant.agent.contract.redteam.RedTeamRun;
/**
 * 当前后台线程的受控执行身份。不是用户入参，只由已取得租约的Worker绑定。
 * 用于D01首次受理多轮探测时验证租约栅栏；finally必须清理，避免线程池身份串用。
 */
public final class RedTeamWorkerContext {
    public record Lease(RedTeamRun run, String workerId) {}
    private static final ThreadLocal<Lease> CURRENT=new ThreadLocal<>();
    private RedTeamWorkerContext() {}
    public static void bind(RedTeamRun run,String workerId) { CURRENT.set(new Lease(run,workerId)); }
    public static Lease current() { return CURRENT.get(); }
    public static void clear() { CURRENT.remove(); }
}
