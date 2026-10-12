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
    /**
     * 该类只包裹HTTP控制面中的数据库幂等竞争。
     * 模型请求、Worker执行和外部副作用不能放入safely，否则重复执行Supplier可能造成重复调用。
     */
    /** 只提供静态控制面保护函数，不持有运行状态。 */
    private SampleFactoryApiSupport() {}

    /**
     * 执行已经过Spring事务代理的控制面动作；唯一键竞争后最多回读一次胜者幂等结果。
     * @param action 不发起模型请求的业务方法，不能是Worker或有外部调用副作用的动作
     * @param <T> 控制面返回类型
     * @return 业务结果或已提交回执；认证/业务异常保留，其他异常转为固定安全错误
     */
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
