package com.chinatelecom.aigc.evaluate.jfree;

import org.jfree.chart.labels.StandardPieSectionLabelGenerator;
import org.jfree.data.general.PieDataset;
import java.text.DecimalFormat;

/**
 * 自定义标签绘制
 */
public class CustomLabelGenerator extends StandardPieSectionLabelGenerator {

    public CustomLabelGenerator() {
        super("{0} ({2})", new DecimalFormat("0.00%"), new DecimalFormat("0.00%"));
    }

    @Override
    public String generateSectionLabel(PieDataset pieDataset, Comparable comparable) {
        Number value = pieDataset.getValue(comparable);
        double val = (value != null) ? value.doubleValue() : 0;

        if (val == 0.001) {
            return comparable + ": 0%";
        }
        return super.generateSectionLabel(pieDataset, comparable);
    }
}
