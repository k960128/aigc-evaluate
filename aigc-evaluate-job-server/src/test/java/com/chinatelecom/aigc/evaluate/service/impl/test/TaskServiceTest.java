package com.chinatelecom.aigc.evaluate.service.impl.test;

import com.chinatelecom.aigc.evaluate.JobServerApplication;
import com.chinatelecom.aigc.evaluate.job.core.handler.JobHandler;
import com.chinatelecom.aigc.evaluate.service.JobService;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.quartz.SchedulerException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest(classes = JobServerApplication.class)
public class TaskServiceTest {
    @Autowired
    private JobService jobService;
    @Test
    public void t1(){
        try {
            jobService.triggerJob(1L);
        } catch (SchedulerException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void t2(){
        jobService.executeTaskJob(1L);
    }
}
