package com.kant.llm.eval.agent.production.support;

import cn.dev33.satoken.exception.SaTokenException;
import com.kant.framework.exception.ClientException;
import com.kant.llm.eval.agent.production.error.SampleFactoryErrorCode;
import org.springframework.dao.DuplicateKeyException;
import java.util.function.Supplier;

/**
 * 仅用于不发起模型调用的HTTP控制面。数据库并发创建竞争可回放一次已经落库的幂等记录。
 * 不得包裹Worker或生成端口，避免把唯一键处理变成模型隐式重发。
 */
public final class SampleFactoryApiSupport {
    private SampleFactoryApiSupport() {}

    public static <T> T safely(Supplier<T> action) {
        try {
            try { return action.get(); }
            catch (DuplicateKeyException collision) {
                // Supplier调用的是Spring事务代理；第一次失败的事务已结束，再读胜者回执。
                return action.get();
            }
        } catch (ClientException | SaTokenException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            // 不把SQL、种子、候选正文、配置凭据或解析栈暴露给前端。
            throw new ClientException("样本工厂读取或受理失败", SampleFactoryErrorCode.PERSISTENCE_ERROR);
        }
    }
}
