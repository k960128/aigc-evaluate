package com.kant.llm;

import com.kant.llm.rag.moduler.EvalQueryTransformer;
import org.jspecify.annotations.NonNull;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.BeansException;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication(scanBasePackages = {
        "com.kant.llm",
        "com.kant.springai.agent"})
@MapperScan(basePackages = {
        "com.kant.*.*.*.dao.mapper",
        "com.kant.springai.agent.dao.mapper"
})
@EnableConfigurationProperties
public class LLMEvalApplication implements ApplicationContextAware {
    static void main(String[] args) {
        SpringApplication.run(LLMEvalApplication.class, args);
    }

    @Override
    public void setApplicationContext(@NonNull ApplicationContext applicationContext) throws BeansException {
        EvalQueryTransformer.setApplicationContext(applicationContext);
    }
}
