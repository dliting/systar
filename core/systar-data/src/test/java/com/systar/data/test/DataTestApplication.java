package com.systar.data.test;

import com.systar.common.config.SystemConfigManager;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication(scanBasePackages = "com.systar.data")
@MapperScan("com.systar.data.mapper")
public class DataTestApplication {
    public static void main(String[] args) {
        SpringApplication.run(DataTestApplication.class, args);
    }

    @Bean
    public SystemConfigManager systemConfigManager() {
        return new SystemConfigManager();
    }
}
