package com.kant.llm;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
@MapperScan(basePackages = {
        "com.kant.llm.*.dao.mapper"
})
public class LLMEvalApplication {
    public static void main(String[] args) {
        SpringApplication.run(LLMEvalApplication.class, args);
    }
}
