package com.chinatelecom.aigc.evaluate.job.core.scheduler;

import com.chinatelecom.aigc.evaluate.job.core.enums.JobDataKeyEnum;
import com.chinatelecom.aigc.evaluate.job.core.handler.JobHandlerInvoker;
import org.quartz.*;

import java.text.SimpleDateFormat;
import java.text.ParseException;
import java.util.Date;

import static com.chinatelecom.aigc.evaluate.common.exception.enums.GlobalErrorCodeConstants.NOT_IMPLEMENTED;
import static com.chinatelecom.aigc.evaluate.common.exception.util.ServiceExceptionUtil.exception0;


/**
 * {@link Scheduler} 的管理器，负责创建任务
 *
 * 考虑到实现的简洁性，我们使用 jobHandlerName 作为唯一标识，即：
 * 1. Job 的 {@link JobDetail#getKey()}
 * 2. Trigger 的 {@link Trigger#getKey()}
 *
 * 另外，jobHandlerName 对应到 Spring Bean 的名字，直接调用
 *
 * @author 后端源码
 */
public class SchedulerManager {

    private final Scheduler scheduler;

    public SchedulerManager(Scheduler scheduler) {
        this.scheduler = scheduler;
    }

    /**
     * 添加 Job 到 Quartz 中
     *
     * @param jobId           任务编号
     * @param jobName         任务名称
     * @param jobHandlerName  任务处理器的名字
     * @param jobHandlerParam 任务处理器的参数
     * @param cronExpression  CRON 表达式
     * @param maxThreadSize   最大线程数
     * @param retryCount      重试次数
     * @param retryInterval   重试间隔
     * @throws SchedulerException 添加异常
     */
    public void addJob(Long jobId, String jobName, String jobHandlerName, String jobHandlerParam,
                       String cronExpression, Integer maxThreadSize, Integer retryCount, Integer retryInterval)
            throws SchedulerException {
        validateScheduler();

        // 创建 JobDetail 对象
        JobDetail jobDetail = JobBuilder.newJob(JobHandlerInvoker.class)
                .usingJobData(JobDataKeyEnum.JOB_ID.name(), jobId)
                .usingJobData(JobDataKeyEnum.JOB_HANDLER_NAME.name(), jobHandlerName)
                .usingJobData(JobDataKeyEnum.JOB_HANDLER_PARAM.name(), jobHandlerParam)
                .usingJobData(JobDataKeyEnum.JOB_MAX_THREAD_SIZE.name(), maxThreadSize)
                .usingJobData(JobDataKeyEnum.JOB_RETRY_COUNT.name(), retryCount != null ? retryCount : 0)
                .usingJobData(JobDataKeyEnum.JOB_RETRY_INTERVAL.name(), retryInterval != null ? retryInterval : 0)
                .withIdentity(jobName) // 任务名称作为唯一标识
                .storeDurably() // 仅存储，不立即执行
                .build();

        // 创建 Trigger 对象
        Trigger trigger = this.buildTrigger(jobName, jobHandlerParam, cronExpression, retryCount, retryInterval);

        // 新增 Job 调度
        scheduler.scheduleJob(jobDetail, trigger);
    }


    public void addDurableJob(Long jobId, String jobName, String handlerName, String handlerParam, Integer maxThreadSize, Integer retryCount, Integer retryInterval) throws SchedulerException {
        JobDetail jobDetail = JobBuilder.newJob(JobHandlerInvoker.class)
                .withIdentity(jobName, "DEFAULT")  // 使用 jobName 作为唯一标识
                .usingJobData(JobDataKeyEnum.JOB_ID.name(), jobId)
                .usingJobData(JobDataKeyEnum.JOB_HANDLER_NAME.name(), handlerName)
                .usingJobData(JobDataKeyEnum.JOB_HANDLER_PARAM.name(), handlerParam)
                .usingJobData(JobDataKeyEnum.JOB_MAX_THREAD_SIZE.name(), maxThreadSize)
                .usingJobData(JobDataKeyEnum.JOB_RETRY_COUNT.name(), retryCount)
                .usingJobData(JobDataKeyEnum.JOB_RETRY_INTERVAL.name(), retryInterval)
                .storeDurably()  // 仅存储，不立即执行
                .build();

        scheduler.addJob(jobDetail, true);
    }

    public void addOneTimeJob(Long jobId, String jobName, String jobHandlerName, String jobHandlerParam, String oneTimeExpression, Integer maxThreadSize)
            throws SchedulerException {
        validateScheduler();

        // 解析 oneTimeExpression 为 Date
        Date startTime;
        try {
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            startTime = dateFormat.parse(oneTimeExpression);
        } catch (ParseException e) {
            throw new IllegalArgumentException("无效的时间格式，应为 yyyy-MM-dd HH:mm:ss");
        }

        JobDetail jobDetail = JobBuilder.newJob(JobHandlerInvoker.class)
                .usingJobData(JobDataKeyEnum.JOB_ID.name(), jobId)
                .usingJobData(JobDataKeyEnum.JOB_HANDLER_NAME.name(), jobHandlerName)
                .usingJobData(JobDataKeyEnum.JOB_HANDLER_PARAM.name(), jobHandlerParam)
                .usingJobData(JobDataKeyEnum.JOB_MAX_THREAD_SIZE.name(), maxThreadSize)
                .withIdentity(jobName)
                .build();

        // 创建 SimpleTrigger 设置具体执行时间
        Trigger trigger = TriggerBuilder.newTrigger()
                .withIdentity(jobName)
                .startAt(startTime) // 一次性任务的执行时间
                .withSchedule(SimpleScheduleBuilder.simpleSchedule().withMisfireHandlingInstructionFireNow())
                .build();

        scheduler.scheduleJob(jobDetail, trigger);
    }



    /**
     * 更新 Job 到 Quartz
     *
     * @param jobHandlerName 任务处理器的名字
     * @param jobHandlerParam 任务处理器的参数
     * @param cronExpression CRON 表达式
     * @param retryCount 重试次数
     * @param retryInterval 重试间隔
     * @throws SchedulerException 更新异常
     */
    public void updateJob(Long jobId, String jobName, String jobHandlerName, String jobHandlerParam,
                          String cronExpression, Integer retryCount, Integer retryInterval)
            throws SchedulerException {
        validateScheduler();
        // 创建新 Trigger 对象
        Trigger newTrigger = this.buildTrigger(jobHandlerName, jobHandlerParam, cronExpression, retryCount, retryInterval);
        // 修改调度
        scheduler.rescheduleJob(new TriggerKey(jobHandlerName), newTrigger);
    }

    /**
     * 删除 Quartz 中的 Job
     *
     * @param jobHandlerName 任务处理器的名字
     * @throws SchedulerException 删除异常
     */
    public void deleteJob(String jobHandlerName) throws SchedulerException {
        validateScheduler();
        // 暂停 Trigger 对象
        scheduler.pauseTrigger(new TriggerKey(jobHandlerName));
        // 取消并删除 Job 调度
        scheduler.unscheduleJob(new TriggerKey(jobHandlerName));
        scheduler.deleteJob(new JobKey(jobHandlerName));
    }

    /**
     * 暂停 Quartz 中的 Job
     *
     * @param jobHandlerName 任务处理器的名字
     * @throws SchedulerException 暂停异常
     */
    public void pauseJob(String jobHandlerName) throws SchedulerException {
        validateScheduler();
        scheduler.pauseJob(new JobKey(jobHandlerName));
    }

    /**
     * 启动 Quartz 中的 Job
     *
     * @param jobHandlerName 任务处理器的名字
     * @throws SchedulerException 启动异常
     */
    public void resumeJob(String jobHandlerName) throws SchedulerException {
        validateScheduler();
        scheduler.resumeJob(new JobKey(jobHandlerName));
        scheduler.resumeTrigger(new TriggerKey(jobHandlerName));
    }

    /**
     * 立即触发一次 Quartz 中的 Job
     *
     * @param jobId           任务编号
     * @param jobName         任务名称（唯一标识）
     * @param jobHandlerName  任务处理器的名字
     * @param jobHandlerParam 任务处理器的参数
     * @param retryCount      任务重试次数
     * @param retryInterval   任务重试间隔（单位秒）
     * @throws SchedulerException 触发异常
     */
    public void triggerJob(Long jobId, String jobName, String jobHandlerName, String jobHandlerParam, Integer maxThreadSize, Integer retryCount, Integer retryInterval)
            throws SchedulerException {
        validateScheduler();

        // 构建 JobDataMap，确保所有参数类型正确
        JobDataMap data = new JobDataMap();
        data.put(JobDataKeyEnum.JOB_ID.name(), jobId);
        data.put(JobDataKeyEnum.JOB_HANDLER_NAME.name(), jobHandlerName);
        data.put(JobDataKeyEnum.JOB_HANDLER_PARAM.name(), jobHandlerParam);
        data.put(JobDataKeyEnum.JOB_MAX_THREAD_SIZE.name(), maxThreadSize);
        data.put(JobDataKeyEnum.JOB_RETRY_COUNT.name(), retryCount);
        data.put(JobDataKeyEnum.JOB_RETRY_INTERVAL.name(), retryInterval);

        JobKey jobKey = new JobKey(jobName);

        // 先检查任务是否存在，避免触发不存在的任务
        if (!scheduler.checkExists(jobKey)) {
            throw new SchedulerException("Job 不存在: " + jobName);
        }

        // 触发任务
        scheduler.triggerJob(jobKey, data);
    }

    /**
     * 构建基于 Cron 表达式的触发器
     *
     * @param jobHandlerName  任务处理器名称（唯一标识）
     * @param jobHandlerParam 任务处理器参数
     * @param cronExpression  任务调度的 Cron 表达式
     * @param retryCount      任务重试次数
     * @param retryInterval   任务重试间隔（单位秒）
     * @return Quartz 触发器对象
     */
    private Trigger buildTrigger(String jobHandlerName, String jobHandlerParam, String cronExpression, Integer retryCount, Integer retryInterval) {
        return TriggerBuilder.newTrigger()
                .withIdentity(jobHandlerName) // 触发器唯一标识
                .withSchedule(CronScheduleBuilder.cronSchedule(cronExpression)) // 配置 Cron 调度
                .usingJobData(JobDataKeyEnum.JOB_HANDLER_PARAM.name(), jobHandlerParam)
                .usingJobData(JobDataKeyEnum.JOB_RETRY_COUNT.name(), retryCount != null ? retryCount : 0)
                .usingJobData(JobDataKeyEnum.JOB_RETRY_INTERVAL.name(), retryInterval != null ? retryInterval : 0)
                .build();
    }

    private void validateScheduler() {
        if (scheduler == null) {
            throw exception0(NOT_IMPLEMENTED.getCode(),
                    "[定时任务 - 已禁用][参考 https://doc.iocoder.cn/job/ 开启]");
        }
    }

}
