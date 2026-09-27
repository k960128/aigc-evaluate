package com.kant.springai.agent.service;

import com.kant.springai.agent.dao.entity.SaProductDO;
import com.kant.springai.agent.dao.entity.SalesOrderDO;
import com.kant.springai.agent.dao.entity.SalesRegionDO;
import com.kant.springai.agent.dao.entity.SalesRepDO;
import com.kant.springai.agent.dao.mapper.SaProductMapper;
import com.kant.springai.agent.dao.mapper.SalesOrderMapper;
import com.kant.springai.agent.dao.mapper.SalesRegionMapper;
import com.kant.springai.agent.dao.mapper.SalesRepMapper;
import com.kant.springai.agent.dto.MonthlyTrendDTO;
import com.kant.springai.agent.dto.SaProductDTO;
import com.kant.springai.agent.dto.SalesRegionDTO;
import com.kant.springai.agent.dto.SalesRepDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SalesQueryService {

    private final SalesRegionMapper salesRegionMapper;
    private final SalesRepMapper salesRepMapper;
    private final SaProductMapper saProductMapper;
    private final SalesOrderMapper salesOrderMapper;


    // ============================================================
    // 基础查询
    // ============================================================

    /**
     * 查询指定时段的订单列表
     * 权限过滤由调用方（工具层）传入 regionId / repId 参数决定
     */
    public List<SalesOrderDO> queryOrders(Long repId, Long regionId,
                                        LocalDate start, LocalDate end) {
        if (repId != null) {
            return salesOrderMapper.findByRepIdAndOrderDateBetween(repId, start, end);
        }
        if (regionId != null) {
            return salesOrderMapper.findByRegionIdAndOrderDateBetween(regionId, start, end);
        }
        return salesOrderMapper.selectList(null).stream()
                .filter(o -> !o.getOrderDate().isBefore(start) && !o.getOrderDate().isAfter(end))
                .collect(Collectors.toList());
    }

    /**
     * 查询总销售额
     */
    public BigDecimal queryTotalAmount(Long regionId, LocalDate start, LocalDate end) {
        if (regionId != null) {
            return salesOrderMapper.sumAmountByRegion(regionId, start, end);
        }
        return salesOrderMapper.selectList(null).stream()
                .filter(o -> "COMPLETED".equals(o.getStatus()))
                .filter(o -> !o.getOrderDate().isBefore(start) && !o.getOrderDate().isAfter(end))
                .map(SalesOrderDO::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // ============================================================
    // 排名查询
    // ============================================================

    /**
     * 销售员业绩排名（带姓名、大区信息）
     */
    public List<SalesRepDTO> queryRepRanking(LocalDate start, LocalDate end, int topN) {
        List<Object[]> raw = salesOrderMapper.findRepRanking(start, end);

        Map<Long, SalesRepDO> repMap = salesRepMapper.selectList(null).stream()
                .collect(Collectors.toMap(SalesRepDO::getId, r -> r));
        Map<Long, String> regionNameMap = salesRegionMapper.selectList(null).stream()
                .collect(Collectors.toMap(SalesRegionDO::getId, SalesRegionDO::getName));

        List<SalesRepDTO> result = new ArrayList<>();
        for (Object[] row : raw) {
            Long repId = ((Number) row[0]).longValue();
            BigDecimal total = new BigDecimal(row[1].toString());
            SalesRepDO rep = repMap.get(repId);
            if (rep == null) continue;

            String regionName = regionNameMap.getOrDefault(rep.getRegionId(), "未知");
            result.add(new SalesRepDTO(repId, rep.getName(), rep.getRegionId(),
                    regionName, total, 0));

            if (result.size() >= topN) break;
        }
        return result;
    }

    /**
     * 大区业绩排名
     */
    public List<SalesRegionDTO> queryRegionRanking(LocalDate start, LocalDate end) {
        List<Object[]> raw = salesOrderMapper.findRegionRanking(start, end);
        Map<Long, String> regionNameMap = salesRegionMapper.selectList(null).stream()
                .collect(Collectors.toMap(SalesRegionDO::getId, SalesRegionDO::getName));

        return raw.stream().map(row -> {
            Long regionId = ((Number) row[0]).longValue();
            BigDecimal total = new BigDecimal(row[1].toString());
            String regionName = regionNameMap.getOrDefault(regionId, "未知");
            return new SalesRegionDTO(regionId, regionName, total, 0, BigDecimal.ZERO);
        }).collect(Collectors.toList());
    }

    /**
     * 产品销售排名
     */
    public List<SaProductDTO> queryProductRanking(LocalDate start, LocalDate end, int topN) {
        List<Object[]> raw = salesOrderMapper.findProductRanking(start, end);
        Map<Long, SaProductDO> productMap = saProductMapper.selectList(null).stream()
                .collect(Collectors.toMap(SaProductDO::getId, p -> p));

        List<SaProductDTO> result = new ArrayList<>();
        for (Object[] row : raw) {
            Long productId = ((Number) row[0]).longValue();
            BigDecimal total = new BigDecimal(row[1].toString());
            Integer qty = ((Number) row[2]).intValue();
            SaProductDO p = productMap.get(productId);
            if (p == null) continue;
            result.add(new SaProductDTO(productId, p.getSkuCode(), p.getName(),
                    p.getCategory(), total, qty));
            if (result.size() >= topN) break;
        }
        return result;
    }

    // ============================================================
    // 趋势分析
    // ============================================================

    /**
     * 月度趋势数据（近 N 个月）
     */
    public List<MonthlyTrendDTO> queryMonthlyTrend(Long regionId, int months) {
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusMonths(months).withDayOfMonth(1);

        List<Object[]> raw = salesOrderMapper.findMonthlyTrend(regionId, start, end);
        return raw.stream().map(row -> new MonthlyTrendDTO(
                row[0].toString(),
                new BigDecimal(row[1].toString()),
                ((Number) row[2]).intValue()
        )).collect(Collectors.toList());
    }

    /**
     * 计算环比增长率（当期 vs 上期）
     */
    public BigDecimal calcGrowthRate(BigDecimal current, BigDecimal previous) {
        if (previous == null || previous.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return current.subtract(previous)
                .divide(previous, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
    }

    // ============================================================
    // 异常检测辅助
    // ============================================================

    /**
     * 查询产品最后一次出单日期
     */
    public LocalDate queryLastOrderDate(Long productId) {
        return salesOrderMapper.findLastOrderDateByProduct(productId);
    }

    /**
     * 查询大区在指定时段内的订单数
     */
    public Long queryOrderCount(Long regionId, LocalDate start, LocalDate end) {
        return salesOrderMapper.countCompletedByRegion(regionId, start, end);
    }

    /**
     * 查询所有销售员退单率
     */
    public List<Object[]> queryRefundRates(LocalDate start, LocalDate end) {
        return salesOrderMapper.findRefundRateByRep(start, end);
    }

    // ============================================================
    // 辅助查询（名称解析）
    // ============================================================

    public String getRepName(Long repId) {
        return Optional.ofNullable(salesRepMapper.selectById(repId))
                .map(SalesRepDO::getName)
                .orElse("未知销售员");
    }

    public String getRegionName(Long regionId) {
        return Optional.ofNullable(salesRegionMapper.selectById(regionId))
                .map(SalesRegionDO::getName)
                .orElse("未知大区");
    }

    public Long getRegionIdByName(String regionName) {
        return salesRegionMapper.findByName(regionName).stream()
                .findFirst()
                .map(SalesRegionDO::getId)
                .orElse(null);
    }

    public Long getRepIdByName(String repName) {
        return salesRepMapper.findByName(repName)
                .map(SalesRepDO::getId)
                .orElse(null);
    }
}