package com.example.edc.dataplane;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@MapperScan("com.example.edc.dataplane.mapper")
public class DataPlaneApplication {
    public static void main(String[] args) {
        SpringApplication.run(DataPlaneApplication.class, args);
    }
}
