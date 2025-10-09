package com.chinatelecom.aigc.evaluate.jfree;

import org.jfree.chart.labels.StandardPieSectionLabelGenerator;
import org.jfree.chart.plot.PiePlotState;
import org.jfree.chart.plot.PlotRenderingInfo;
import org.jfree.chart.plot.PlotState;
import org.jfree.chart.plot.RingPlot;
import org.jfree.data.general.DefaultPieDataset;

import java.awt.*;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

/**
 * 自定义 RingPlot 类，用于绘制中心文本
 */
public class CustomRingPlot extends RingPlot {

    private final String centerText;

    public CustomRingPlot(DefaultPieDataset dataset, String centerText) {
        super(dataset);
        this.centerText = centerText;
        setLabelFont(new java.awt.Font("黑体", java.awt.Font.BOLD, 25));
        setLabelOutlinePaint(null);
        setLabelPaint(Color.BLACK);
        setLabelBackgroundPaint(Color.WHITE);
    }

    @Override
    public void draw(Graphics2D g2, Rectangle2D area, Point2D anchor, PlotState parentState, PlotRenderingInfo info) {
        super.draw(g2, area, anchor, parentState, info);
        // 绘制中心文本
        Font font = new Font("黑体", Font.BOLD, 24);
        g2.setFont(font);
        g2.setColor(Color.RED);
        g2.drawString(centerText, (float) (area.getX() + area.getWidth() / 2 - 95),
                (float) (area.getY() + area.getHeight() / 2 + 10));
    }
}
