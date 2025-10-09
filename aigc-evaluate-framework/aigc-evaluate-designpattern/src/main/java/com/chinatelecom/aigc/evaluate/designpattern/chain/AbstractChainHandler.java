package com.chinatelecom.aigc.evaluate.designpattern.chain;

import org.springframework.core.Ordered;

public interface AbstractChainHandler<T> extends Ordered {

    /**
     * 责任链执行逻辑
     * @param requestParam 责任链入参
     */
    void handler(T requestParam);

    /**
     * 责任链表示
     * @return 责任链组件标识
     */
    String mark();

}
