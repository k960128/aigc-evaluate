package com.chinatelecom.aigc.evaluate.common.util.file;

import cn.hutool.extra.spring.SpringUtil;
import org.apache.commons.lang3.StringUtils;

import java.io.File;

/**
 * File 工具类
 *
 * @author AIGC
 */
public class FileUtil {

    /**
     * 获取默认文件路径
     * @return 默认文件存储路径
     */
    public static String getReportPath() {
        String reportPath = SpringUtil.getProperty("aigc.file.path");
        if (StringUtils.isBlank(reportPath)) {
            return System.getProperty("user.dir") + File.separator + "report" + File.separator;
        }
        return reportPath;
    }
}
