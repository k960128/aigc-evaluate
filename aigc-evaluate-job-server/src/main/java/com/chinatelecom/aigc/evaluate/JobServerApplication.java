package com.chinatelecom.aigc.evaluate;

import com.chinatelecom.aigc.evaluate.common.config.AutoEvaluateAutoConfiguration;
import com.github.xiaoymin.knife4j.spring.annotations.EnableKnife4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import springfox.documentation.swagger2.annotations.EnableSwagger2;

@EnableKnife4j
@EnableSwagger2
@SpringBootApplication
@EnableConfigurationProperties(AutoEvaluateAutoConfiguration.class)
public class JobServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(JobServerApplication.class);
    }
}
