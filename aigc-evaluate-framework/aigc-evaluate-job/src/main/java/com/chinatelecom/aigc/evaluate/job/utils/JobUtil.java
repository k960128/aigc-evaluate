package com.chinatelecom.aigc.evaluate.job.utils;
import com.chinatelecom.aigc.evaluate.job.core.util.CronUtils;

import static com.chinatelecom.aigc.evaluate.common.exception.util.ServiceExceptionUtil.exception;
import static com.chinatelecom.aigc.evaluate.job.enums.error.ErrorCodeConstants.*;

public class JobUtil {



    public static void validateCronExpression(String cronExpression) {
        if (!CronUtils.isValid(cronExpression)) {
            throw exception(JOB_CRON_EXPRESSION_VALID);
        }
    }
}
