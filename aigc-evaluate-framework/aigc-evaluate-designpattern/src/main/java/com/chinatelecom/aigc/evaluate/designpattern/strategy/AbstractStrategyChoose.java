package com.chinatelecom.aigc.evaluate.designpattern.strategy;


import com.chinatelecom.aigc.evaluate.common.exception.ServiceException;
import com.chinatelecom.aigc.evaluate.common.util.spring.SpringUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import static com.chinatelecom.aigc.evaluate.common.exception.enums.GlobalErrorCodeConstants.DUPLICATE_EXECUTION_POLICY;
import static com.chinatelecom.aigc.evaluate.common.exception.enums.GlobalErrorCodeConstants.STRATEGY_UNDEFINED;

/**
 * 策略选择器
 * @author AIGC
 */
public class AbstractStrategyChoose implements InitializingBean {

    /**
     * 执行策略集合
     */
    private final Map<String, AbstractExecuteStrategy> abstractExecuteStrategyMap = new HashMap<>();

    /**
     * 根据 mark 查询具体策略
     *
     * @param mark          策略标识
     * @param predicateFlag 匹配范解析标识
     * @return 实际执行策略
     */
    public AbstractExecuteStrategy choose(String mark, Boolean predicateFlag) {
        if (predicateFlag != null && predicateFlag) {
            return abstractExecuteStrategyMap.values().stream()
                    .filter(each -> StringUtils.hasText(each.patternMatchMark()))
                    .filter(each -> Pattern.compile(each.patternMatchMark()).matcher(mark).matches())
                    .findFirst()
                    .orElseThrow(() -> new ServiceException(STRATEGY_UNDEFINED));
        }
        return Optional.ofNullable(abstractExecuteStrategyMap.get(mark))
                .orElseThrow(() -> new ServiceException(STRATEGY_UNDEFINED));
    }

    /**
     * 根据 mark 查询具体策略并执行
     *
     * @param mark         策略标识
     * @param requestParam 执行策略入参
     * @param <REQUEST>    执行策略入参范型
     */
    public <REQUEST> void chooseAndExecute(String mark, REQUEST requestParam) {
        AbstractExecuteStrategy executeStrategy = choose(mark, null);
        executeStrategy.execute(requestParam);
    }

    /**
     * 根据 mark 查询具体策略并执行
     *
     * @param mark          策略标识
     * @param requestParam  执行策略入参
     * @param predicateFlag 匹配范解析标识
     * @param <REQUEST>     执行策略入参范型
     */
    public <REQUEST> void chooseAndExecute(String mark, REQUEST requestParam, Boolean predicateFlag) {
        AbstractExecuteStrategy executeStrategy = choose(mark, predicateFlag);
        executeStrategy.execute(requestParam);
    }

    /**
     * 根据 mark 查询具体策略并执行，带返回结果
     *
     * @param mark         策略标识
     * @param requestParam 执行策略入参
     * @param <REQUEST>    执行策略入参范型
     * @param <RESPONSE>   执行策略出参范型
     * @return
     */
    public <REQUEST, RESPONSE> RESPONSE chooseAndExecuteResp(String mark, REQUEST requestParam) {
        AbstractExecuteStrategy executeStrategy = choose(mark, null);
        return (RESPONSE) executeStrategy.executeResp(requestParam);
    }

    @Override
    public void afterPropertiesSet() {
        Map<String, AbstractExecuteStrategy> actual = SpringUtils.getApplicationContext().getBeansOfType(AbstractExecuteStrategy.class);
        actual.forEach((beanName, bean) -> {
            AbstractExecuteStrategy beanExist = abstractExecuteStrategyMap.get(bean.mark());
            if (beanExist != null) {
                throw new ServiceException(DUPLICATE_EXECUTION_POLICY);
            }
            abstractExecuteStrategyMap.put(bean.mark(), bean);
        });
    }
}
