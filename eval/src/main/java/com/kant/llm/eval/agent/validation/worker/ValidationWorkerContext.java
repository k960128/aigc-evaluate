package com.kant.llm.eval.agent.validation.worker;
/** 验证Worker的线程身份。HTTP线程不能伪造租约直接发出验证探测。 */
public final class ValidationWorkerContext {
    private static final ThreadLocal<Lease> CURRENT=new ThreadLocal<>();
    private ValidationWorkerContext() {}
    public record Lease(String runId,String workerId,String tenantId,String actorId) {}
    public static void bind(Lease lease) { CURRENT.set(lease); }
    public static Lease current() { return CURRENT.get(); }
    public static void clear() { CURRENT.remove(); }
}
