package com.systar.server;

import com.systar.server.security.TokenService;
import jakarta.annotation.PreDestroy;
import org.mybatis.spring.annotation.MapperScan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@SpringBootApplication(scanBasePackages = "com.systar")
@MapperScan({"com.systar.data.mapper", "com.systar.ops.**.mapper", "com.systar.system.**.mapper"})
@EnableAsync
@EnableScheduling
@EnableCaching
public class SystarApplication {

    private static final Logger log = LoggerFactory.getLogger(SystarApplication.class);
    private static final Path PID_FILE = Paths.get("temp/backend.pid").toAbsolutePath();

    private static void writePidFile() {
        try {
            Files.createDirectories(PID_FILE.getParent());
            Files.writeString(PID_FILE, String.valueOf(ProcessHandle.current().pid()));
            log.info("PID {} written to {}", ProcessHandle.current().pid(), PID_FILE);
        } catch (IOException e) {
            log.error("Failed to write PID file: {}", PID_FILE, e);
        }
    }

    @PreDestroy
    void deletePidFile() {
        try {
            Files.deleteIfExists(PID_FILE);
            log.info("PID file deleted: {}", PID_FILE);
        } catch (IOException e) {
            log.warn("Failed to delete PID file: {}", PID_FILE, e);
        }
    }

    public static void main(String[] args) {
        writePidFile();
        SpringApplication.run(SystarApplication.class, args);
    }

    @Bean
    TokenService tokenService(@Value("${systar.security.secret}") String secret) {
        return new TokenService(secret, 30);
    }
}
