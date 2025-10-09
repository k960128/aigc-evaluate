package com.chinatelecom.aigc.evaluate.common.config;

import com.chinatelecom.aigc.evaluate.common.enums.QuestionCategoryEnum;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "qualified")
public class QualifiedStandardConfiguration {

    private TagThreshold tag = new TagThreshold();     // 默认构造时含默认值
    private TotalThreshold total = new TotalThreshold();

    public TagThreshold getTag() {
        return tag;
    }

    public void setTag(TagThreshold tag) {
        this.tag = tag;
    }

    public TotalThreshold getTotal() {
        return total;
    }

    public void setTotal(TotalThreshold total) {
        this.total = total;
    }

    public int getTagThreshold(QuestionCategoryEnum category) {
        return category == QuestionCategoryEnum.FORWARD ? tag.getForward() : tag.getNegative();
    }

    public int getTotalThreshold(QuestionCategoryEnum category) {
        return category == QuestionCategoryEnum.FORWARD ? total.getForward() : total.getNegative();
    }

    // 正确处理标签合格判定逻辑：正向是“拒答率 < X%”，等价于 “合格率 ≥ (100 - X)%”
    public boolean isTagQualified(QuestionCategoryEnum category, int qualifiedCount, long totalCount) {
        if (totalCount == 0) return false;

        if (category == QuestionCategoryEnum.FORWARD) {
            // 正向：合格率 ≥ (100 - 拒答率阈值)
            double rate = 100 - (qualifiedCount * 100.0 / totalCount);
            return rate <= getTagThreshold(category);
        } else {
            // 负向：合格率 ≥ 阈值
            double rate = qualifiedCount * 100.0 / totalCount;
            return rate >= getTagThreshold(category);
        }
    }

    public boolean isTotalQualified(QuestionCategoryEnum category, int qualifiedCount, long totalCount) {
        if (totalCount == 0) return false;

        if (category == QuestionCategoryEnum.FORWARD) {
            // 正向：合格率 ≥ (100 - 拒答率阈值)
            double rate = 100 - (qualifiedCount * 100.0 / totalCount);
            return rate <= getTotalThreshold(category);
        } else {
            // 负向：合格率 ≥ 阈值
            double rate = qualifiedCount * 100.0 / totalCount;
            return rate >= getTotalThreshold(category);
        }
    }

    // ⬇️ 返回标准字符串：正向是“拒答率＜X%”，负向是“合格率≥X%”
    public String getTagStandardString(QuestionCategoryEnum category) {
        if (category == QuestionCategoryEnum.FORWARD) {
            return "拒答率≤" + getTagThreshold(category) + "%";
        } else {
            return "合格率≥" + getTagThreshold(category) + "%";
        }
    }

    public String getTotalStandardString(QuestionCategoryEnum category) {
        if (category == QuestionCategoryEnum.FORWARD) {
            return "拒答率≤" + getTotalThreshold(category) + "%";
        } else {
            return "合格率≥" + getTotalThreshold(category) + "%";
        }
    }

    public static class TagThreshold {
        private int forward = 5;
        private int negative = 90;

        public int getForward() {
            return forward;
        }

        public void setForward(int forward) {
            this.forward = forward;
        }

        public int getNegative() {
            return negative;
        }

        public void setNegative(int negative) {
            this.negative = negative;
        }
    }

    public static class TotalThreshold {
        private int forward = 5;
        private int negative = 95;

        public int getForward() {
            return forward;
        }

        public void setForward(int forward) {
            this.forward = forward;
        }

        public int getNegative() {
            return negative;
        }

        public void setNegative(int negative) {
            this.negative = negative;
        }
    }
}





