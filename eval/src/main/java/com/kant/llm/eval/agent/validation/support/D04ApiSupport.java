package com.kant.llm.eval.agent.validation.support;
import com.kant.framework.exception.ClientException;
import com.kant.llm.eval.agent.finding.error.AgentFindingErrorCode;
import java.util.function.Supplier;
/** 控制面异常收口：不把数据库SQL、固定输入、响应或凭据带到接口错误。 */
public final class D04ApiSupport {
    private D04ApiSupport() {}
    public static <T> T safely(Supplier<T> action) {
        try { return action.get(); }
        catch(ClientException ex) { throw ex; }
        catch(cn.dev33.satoken.exception.SaTokenException ex) { throw ex; }
        catch(RuntimeException ex) { throw new ClientException("D04事实读取或受理失败",AgentFindingErrorCode.PERSISTENCE_ERROR); }
    }
}
