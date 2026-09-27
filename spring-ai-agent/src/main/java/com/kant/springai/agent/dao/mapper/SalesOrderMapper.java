package com.kant.springai.agent.dao.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kant.springai.agent.dao.entity.SalesOrderDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Mapper
public interface SalesOrderMapper extends BaseMapper<SalesOrderDO> {

    default List<SalesOrderDO> findByRepIdAndOrderDateBetween(Long repId, LocalDate start, LocalDate end) {
        return selectList(new LambdaQueryWrapper<SalesOrderDO>()
                .eq(SalesOrderDO::getRepId, repId)
                .between(SalesOrderDO::getOrderDate, start, end));
    }

    default List<SalesOrderDO> findByRegionIdAndOrderDateBetween(Long regionId, LocalDate start, LocalDate end) {
        return selectList(new LambdaQueryWrapper<SalesOrderDO>()
                .eq(SalesOrderDO::getRegionId, regionId)
                .between(SalesOrderDO::getOrderDate, start, end));
    }

    default List<SalesOrderDO> findByProductIdAndOrderDateBetween(Long productId, LocalDate start, LocalDate end) {
        return selectList(new LambdaQueryWrapper<SalesOrderDO>()
                .eq(SalesOrderDO::getProductId, productId)
                .between(SalesOrderDO::getOrderDate, start, end));
    }

    @Select("SELECT COALESCE(SUM(amount), 0) FROM sa_sales_order " +
            "WHERE region_id = #{regionId} AND status = 'COMPLETED' " +
            "AND order_date BETWEEN #{start} AND #{end}")
    BigDecimal sumAmountByRegion(@Param("regionId") Long regionId,
                                  @Param("start") LocalDate start,
                                  @Param("end") LocalDate end);

    @Select("SELECT COALESCE(SUM(amount), 0) FROM sa_sales_order " +
            "WHERE rep_id = #{repId} AND status = 'COMPLETED' " +
            "AND order_date BETWEEN #{start} AND #{end}")
    BigDecimal sumAmountByRep(@Param("repId") Long repId,
                               @Param("start") LocalDate start,
                               @Param("end") LocalDate end);

    @Select("SELECT rep_id, SUM(amount) AS total FROM sa_sales_order " +
            "WHERE status = 'COMPLETED' AND order_date BETWEEN #{start} AND #{end} " +
            "GROUP BY rep_id ORDER BY total DESC")
    List<Map<String, Object>> findRepRanking(@Param("start") LocalDate start,
                                   @Param("end") LocalDate end);

    @Select("SELECT region_id, SUM(amount) AS total FROM sa_sales_order " +
            "WHERE status = 'COMPLETED' AND order_date BETWEEN #{start} AND #{end} " +
            "GROUP BY region_id ORDER BY total DESC")
    List<Map<String, Object>> findRegionRanking(@Param("start") LocalDate start,
                                      @Param("end") LocalDate end);

    @Select("SELECT product_id, SUM(amount) AS total, SUM(quantity) AS qty " +
            "FROM sa_sales_order WHERE status = 'COMPLETED' " +
            "AND order_date BETWEEN #{start} AND #{end} " +
            "GROUP BY product_id ORDER BY total DESC")
    List<Map<String, Object>> findProductRanking(@Param("start") LocalDate start,
                                       @Param("end") LocalDate end);

    @Select("SELECT DATE_FORMAT(order_date, '%Y-%m') AS month, " +
            "SUM(amount) AS total, COUNT(*) AS order_count " +
            "FROM sa_sales_order WHERE status = 'COMPLETED' " +
            "AND (#{regionId, jdbcType=BIGINT} IS NULL OR region_id = #{regionId, jdbcType=BIGINT}) " +
            "AND order_date >= #{start} AND order_date < #{end} " +
            "GROUP BY month ORDER BY month")
    List<Map<String, Object>> findMonthlyTrend(@Param("regionId") Long regionId,
                                               @Param("start") LocalDate start,
                                               @Param("end") LocalDate end);

    @Select("SELECT MAX(order_date) FROM sa_sales_order " +
            "WHERE product_id = #{productId} AND status = 'COMPLETED'")
    LocalDate findLastOrderDateByProduct(@Param("productId") Long productId);

    @Select("SELECT rep_id, " +
            "SUM(CASE WHEN status = 'REFUNDED' THEN 1 ELSE 0 END) AS refunded, " +
            "COUNT(*) AS total " +
            "FROM sa_sales_order WHERE order_date BETWEEN #{start} AND #{end} " +
            "GROUP BY rep_id")
    List<Map<String, Object>> findRefundRateByRep(@Param("start") LocalDate start,
                                        @Param("end") LocalDate end);

    @Select("SELECT COUNT(*) FROM sa_sales_order " +
            "WHERE region_id = #{regionId} AND status = 'COMPLETED' " +
            "AND order_date BETWEEN #{start} AND #{end}")
    Long countCompletedByRegion(@Param("regionId") Long regionId,
                                 @Param("start") LocalDate start,
                                 @Param("end") LocalDate end);
}