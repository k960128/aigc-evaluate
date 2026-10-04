package com.kant.agent.contract.production;

import com.kant.agent.contract.common.ContractChecks;

import java.time.*;

/**
 * 有界每日生产计划，不接受任意cron或由模型编造调度命令。
 * @param zoneId IANA时区，例如Asia/Shanghai
 * @param dailyAt 时区内每日计划时刻；秒和纳秒必须为0
 * @param startDate 首个计划日期
 * @param endDate 最后计划日期，最多覆盖366天
 * @param misfirePolicy SKIP漏触发不生产；FIRE_LATEST只补最新到期槽位，较早遗漏留汇总审计
 * @param graceSeconds SKIP允许正常调度延迟的秒数
 */
public record ProductionSchedule(String zoneId, LocalTime dailyAt, LocalDate startDate, LocalDate endDate,
                                 MisfirePolicy misfirePolicy, int graceSeconds) {
    /** SKIP丢弃超宽限槽位；FIRE_LATEST只补最新到期槽位，其余遗漏由服务记录审计。 */
    public enum MisfirePolicy {SKIP, FIRE_LATEST}

    /** 校验有效时区、有界日期和分钟精度，不执行调度，也不读取服务器默认时区。 */
    public ProductionSchedule {
        ZoneId.of(ContractChecks.notBlank(zoneId, "zoneId"));
        ContractChecks.required(dailyAt, "dailyAt");
        ContractChecks.required(startDate, "startDate");
        ContractChecks.required(endDate, "endDate");
        ContractChecks.required(misfirePolicy, "misfirePolicy");
        ContractChecks.require(startDate.getYear() >= 1000 && endDate.getYear() <= 9999, "计划日期超出数据库支持范围");
        ContractChecks.require(!endDate.isBefore(startDate) && java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate) <= 365, "计划日期范围最多366天");
        ContractChecks.require(dailyAt.getSecond() == 0 && dailyAt.getNano() == 0, "计划时刻精确到分钟");
        ContractChecks.require(graceSeconds >= 30 && graceSeconds <= 3600, "漏触发宽限为30至3600秒");
    }

    /** DST缺失时刻顺延，重复时刻选较早偏移；同一天只生成一个实际Instant槽位。 */
    public Instant at(LocalDate date) {
        return ZonedDateTime.of(date, dailyAt, ZoneId.of(zoneId)).toInstant();
    }

    /** 从原槽位所在本地日期推进一天，不是简单增加24小时；超过结束日期返回null。 */
    public Instant next(Instant previous) {
        LocalDate date = previous.atZone(ZoneId.of(zoneId)).toLocalDate().plusDays(1);
        return date.isAfter(endDate) ? null : at(date);
    }

    /** 判断UTC时点是否精确对应范围内的原计划槽位，不能用实际请求时刻冒充补跑槽位。 */
    public boolean isSlot(Instant instant) {
        LocalDate date = instant.atZone(ZoneId.of(zoneId)).toLocalDate();
        return !date.isBefore(startDate) && !date.isAfter(endDate) && at(date).equals(instant);
    }

    /** 计算最新到期槽位，避免长时间停机后发出一串旧计划的付费调用。 */
    public Instant latestDue(Instant now) {
        LocalDate date = now.atZone(ZoneId.of(zoneId)).toLocalDate();
        if (date.isAfter(endDate)) date = endDate;
        if (at(date).isAfter(now)) date = date.minusDays(1);
        return date.isBefore(startDate) ? null : at(date);
    }
}
