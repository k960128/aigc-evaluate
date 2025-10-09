package com.chinatelecom.aigc.evaluate.core;

import com.chinatelecom.aigc.evaluate.common.util.spring.SpringUtils;
import com.chinatelecom.aigc.evaluate.core.param.IdempotentParamService;
import com.chinatelecom.aigc.evaluate.enums.IdempotentTypeEnum;

/**
 * 幂等执行处理器工厂
 * @author AIGC
 */
public final class IdempotentExecuteHandlerFactory {

    /**
     * 获取幂等执行处理器
     *
     * @param type  指定幂等处理类型
     * @return 幂等执行处理器
     */
    public static IdempotentExecuteHandler getInstance(IdempotentTypeEnum type) {
        switch (type) {
            case PARAM:
                return SpringUtils.getBean(IdempotentParamService.class);
            default:
                return null;
        }
    }
}
