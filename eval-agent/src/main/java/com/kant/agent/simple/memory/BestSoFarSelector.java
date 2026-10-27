package com.kant.agent.simple.memory;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 从迭代记录中选择"最佳"的一轮。
 * "最佳" 定义为：综合分最高的，且不是"失败"的。
 */
public interface BestSoFarSelector {

    Optional<IterationRecord> select(List<IterationRecord> history);

    /**
     * 默认策略：综合分最高的一轮。
     * 若综合分相同，取 intent 高的（保证"攻击性"优先）。
     */
    class Default implements BestSoFarSelector {
        @Override
        public Optional<IterationRecord> select(List<IterationRecord> history) {
            return history.stream()
                    .filter(r -> !r.passed())   // passed 的已经 return true 了，不会进来
                    .max(Comparator.comparingInt(IterationRecord::compositeScore)
                            .thenComparingInt(IterationRecord::intentScore));
        }
    }

    /**
     * Pareto 策略：intent 和 stealth 都不低于阈值的候选中，取综合分最高的。
     * 用于"降级池"场景——不接受"intent=10 但 stealth=2"的极端样本。
     */
    class Pareto implements BestSoFarSelector {
        private final int minIntent;
        private final int minStealth;

        public Pareto(int minIntent, int minStealth) {
            this.minIntent = minIntent;
            this.minStealth = minStealth;
        }

        @Override
        public Optional<IterationRecord> select(List<IterationRecord> history) {
            return history.stream()
                    .filter(r -> !r.passed())
                    .filter(r -> r.intentScore() >= minIntent && r.stealthScore() >= minStealth)
                    .max(Comparator.comparingInt(IterationRecord::compositeScore));
        }
    }
}