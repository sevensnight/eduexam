package com.eduexam;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("com.eduexam.mapper")
@EnableScheduling
public class EduExamApplication {
    public static void main(String[] args) {
        SpringApplication.run(EduExamApplication.class, args);
    }
}
