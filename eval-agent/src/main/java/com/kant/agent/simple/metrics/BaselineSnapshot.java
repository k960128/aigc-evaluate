package com.kant.agent.simple.metrics;

import java.time.Instant;
import java.util.Map;

/**
 * 一次 baseline 快照，用于"优化前 vs 优化后"的数字对比。
 *
 * <p>在样本工厂的迭代优化过程中，每完成一个里程碑（优化前基线、去重后、预算后、
 * P2 后），调一次 {@code POST /sample-factory/baseline/snapshot?label=xxx} 采集一份快照。
 * 后续对比时，用 {@code latestByLabel("p0-p1_baseline")} 与 {@code latestByLabel("after_dedup")}
 * 取出两份快照，逐字段求差，即可量化"这次优化到底改善了多少"。</p>
 *
 * <h3>三个核心用途</h3>
 * <ul>
 *   <li><b>优化效果验证</b>：P1 ③ 去重做完，对比"重复率/Judge 调用次数"是否下降；
 *       P1 ④ 预算做完，对比"平均轮次/token/ticket"是否下降；P2 Ensemble 做完，
 *       对比"通过率"是否提升。</li>
 *   <li><b>质量报告的数据源</b>：L2-C 的 {@code QualityReport.trend} 板块直接消费
 *       两份快照的差值，渲染成"baseline vs 本批"的趋势表。</li>
 *   <li><b>回归与告警</b>：若某次优化后 {@code passRate} 反而下降，可快速定位
 *       "这次改动引入了退化"，避免"凭感觉优化"。</li>
 * </ul>
 *
 * <h3>设计要点</h3>
 * <ul>
 *   <li><b>快照是"瞬时值"不是"累计值"</b>：{@code snapshotId} + {@code createdAt}
 *       唯一标识一次采集；同一 {@code label} 可采多次（如重跑验证），
 *       {@code latestByLabel} 取 {@code createdAt} 最新的一条。</li>
 *   <li><b>label 是版本标签，不是枚举</b>：约定为 {@code <阶段>_<做了什么>}，
 *       如 {@code p0-p1_baseline} / {@code after_dedup} / {@code after_p2}。
 *       命名一致是"能对比"的前提，但代码层不做枚举强约束，保留灵活性。</li>
 *   <li><b>比率用 0~1 小数，不用百分比字符串</b>：{@code passRate=0.87} 而非
 *       {@code "87%"}，程序消费方直接可用；展示层自己 ×100。避免"字符串解析"
 *       这种无意义的往返。</li>
 *   <li><b>"每 ticket 均值"而非"绝对次数"</b>：{@code intentEmptyPerTicket} 是
 *       "意图归零拦截次数 / ticketsTried"，因为不同批次的 ticket 数不同，
 *       绝对次数不可比；归一化后才能跨批次对比。</li>
 *   <li><b>extraMetrics 预留扩展</b>：未来若想加"平均 token"、"平均耗时 P90"、
 *       "Gate 命中率"等，直接往 map 里塞，不改 record 结构，不破坏已有消费方。</li>
 * </ul>
 *
 * <h3>与 Micrometer 的关系</h3>
 * <p>本 record 本身是<b>快照</b>，不直接对接 Micrometer。数据来源是
 * {@code MeterRegistry} 里的累计值，由 {@code BaselineController} 在采集时
 * 读取并计算成快照。这意味着：</p>
 * <ul>
 *   <li>采集必须在"干净区间"内进行——同一进程采两次，第二次是累计混算的。
 *       当前工作流是"跑一批 → 采一次 → 重启 → 再采"，天然干净；
 *       若做自动化采集，需改用差值或 Prometheus 侧 {@code increase()}。</li>
 *   <li>快照一旦落盘即与 Micrometer 解耦，消费方只读快照，不再碰 registry。</li>
 * </ul>
 *
 * <h3>不可变性</h3>
 * <p>record 天然不可变。{@code extraMetrics} 是 {@code Map}，本 record 不做深拷贝——
 * 调用方不应在创建后修改该 map（约定：由 {@code BaselineController} 构造后立即落盘）。</p>
 *
 * @param snapshotId           快照唯一 ID（UUID），用于去重、查询、审计
 * @param createdAt            采集时间戳（UTC），{@code latestByLabel} 按此排序
 * @param label                版本标签，如 {@code p0-p1_baseline} / {@code after_dedup} /
 *                             {@code after_budget} / {@code after_p2} / {@code final}。
 *                             命名约定：{@code <阶段>_<做了什么>}，全小写 + 下划线
 * @param ticketsTried         本批尝试的 ticket 数（= passedCount + degradedCount + failedCount）
 * @param passedCount          Judge 在 maxIterations 内通过评审的 ticket 数
 *                             （{@code outcome=passed}）
 * @param degradedCount        3 轮全失败但有 best-so-far 的 ticket 数
 *                             （{@code outcome=degraded}，可进降级池人工复核）
 * @param failedCount          3 轮全失败且无 best-so-far（history 为空）的 ticket 数
 *                             （{@code outcome=failed}，通常是模型调用异常或 early bail）
 * @param passRate             {@code passedCount / ticketsTried}，0~1 小数。
 *                             这是<b>最重要的质量指标</b>，优化效果对比首先看它
 * @param r1IntentPreservedRate R1（首轮）intent>=5 的 ticket / ticketsTried，0~1 小数。
 *                             衡量"P0 意图锚点是否生效"——P0 改造前该值接近 0，
 *                             改造后应显著提升。注意与通过线（双 8）口径不同，
 *                             不要与 {@code passRate} 混读
 * @param intentEmptyPerTicket 意图归零拦截次数 / ticketsTried。
 *                             非零说明 L1 拦截在正常工作；持续偏高说明
 *                             Generator 首轮频繁跑偏，需调 prompt 或扩 seed
 * @param adviceFilteredPerTicket advice 安全对齐话术被过滤次数 / ticketsTried。
 *                             非零说明 Judge 偶尔"安全对齐复发"，过滤器在兜底；
 *                             持续偏高说明 Judge 立场约束需加强或换专门训练的 Judge
 * @param avgIterations        平均迭代轮次。优化效果对比看它是否下降——
 *                             编码确定性化（P1 ②）完成后该值应明显下降，
 *                             因为不再有"LLM 编错密文 → 打回重写"的浪费
 * @param avgDurationMs        平均耗时（毫秒）。注意：这是<b>单 ticket 全流程耗时</b>，
 *                             包含多次 LLM 调用 + Composer 变换 + Judge。
 *                             与 {@code avgIterations} 结合看，能判断"是轮次少了还是单轮慢了"
 * @param extraMetrics         预留扩展指标，key 为指标名、value 为数值。当前包含：
 *                             <ul>
 *                               <li>{@code avgIntentScoreR1/R2/R3}：各轮 intent 均值，
 *                                   用于观察"逐轮意图是否提升"</li>
 *                               <li>{@code avgStealthScoreR1}：R1 stealth 均值</li>
 *                               <li>{@code avgBestSoFarComposite}：降级池样本的平均综合分，
 *                                   衡量"降级样本的质量水位"</li>
 *                             </ul>
 *                             未来可加 {@code avgTokensPerTicket}、{@code gateDuplicateRate}、
 *                             {@code transformerIncompleteRate} 等
 */
public record BaselineSnapshot(
        String snapshotId,
        Instant createdAt,
        String label,
        int ticketsTried,
        int passedCount,
        int degradedCount,
        int failedCount,
        double passRate,
        double r1IntentPreservedRate,
        double intentEmptyPerTicket,
        double adviceFilteredPerTicket,
        double avgIterations,
        double avgDurationMs,
        Map<String, Double> extraMetrics
) {

    /**
     * 校验快照的内部一致性：{@code ticketsTried} 应等于三类 outcome 之和，
     * {@code passRate} 应等于 {@code passedCount / ticketsTried}（容差 0.001）。
     *
     * <p>用途：落盘前自检，避免"采集逻辑写错导致字段对不上"的脏数据入库。
     * 例如 P0-FIX-2 修之前，成功分支不调 {@code collect}，会出现
     * {@code ticketsTried > 0 但 passedCount = 0} 的矛盾，本方法能立刻暴露。</p>
     *
     * @return 若一致返回 {@code true}；否则返回 {@code false}（调用方可据此告警或拒绝落盘）
     */
    public boolean isConsistent() {
        if (ticketsTried != passedCount + degradedCount + failedCount) {
            return false;
        }
        if (ticketsTried == 0) {
            return passRate == 0.0;
        }
        double expected = (double) passedCount / ticketsTried;
        return Math.abs(passRate - expected) < 0.001;
    }

    /**
     * 计算本快照相对另一份基线快照的通过率变化。
     *
     * <p>便捷方法，避免调用方每次手写 {@code current.passRate() - baseline.passRate()}。
     * 返回值为正表示改善，为负表示退化。</p>
     *
     * @param baseline 基线快照（通常是 {@code p0-p1_baseline}）
     * @return 通过率差值，范围 -1.0 ~ 1.0
     */
    public double passRateDelta(BaselineSnapshot baseline) {
        return this.passRate - baseline.passRate;
    }

    /**
     * 判断本快照的通过率是否相对基线有显著改善。
     *
     * <p>"显著"定义为提升 >= 5 个百分点（0.05）。这是经验阈值——
     * 低于此值可能是随机波动，高于此值才值得在质量报告里标为"IMPROVED"。</p>
     *
     * @param baseline 基线快照
     * @return 若提升 >= 0.05 返回 {@code true}
     */
    public boolean isImprovedOver(BaselineSnapshot baseline) {
        return passRateDelta(baseline) >= 0.05;
    }
}