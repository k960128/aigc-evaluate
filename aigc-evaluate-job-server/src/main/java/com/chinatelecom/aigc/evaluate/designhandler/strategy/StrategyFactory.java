package com.chinatelecom.aigc.evaluate.designhandler.strategy;

import com.chinatelecom.aigc.evaluate.designpattern.strategy.AbstractStrategyChoose;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class StrategyFactory {

    private final AbstractStrategyChoose abstractStrategyChoose;

    public StrategyFactory(AbstractStrategyChoose abstractStrategyChoose) {
        this.abstractStrategyChoose = abstractStrategyChoose;
    }

}
