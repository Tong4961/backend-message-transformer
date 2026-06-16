package com.dsl;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("com.dsl.mapper")
@EnableScheduling
public class DslApplication {
    public static void main(String[] args) {
        SpringApplication.run(DslApplication.class, args);
    }
}
