package com.systar.server.config;

import com.systar.common.code.CodeDictManager;
import com.systar.common.config.SystemConfigManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration for application-level beans.
 * <p>
 * Registers pure-Java utility classes as Spring beans so they can be
 * injected via {@code @Autowired} or constructor injection throughout
 * the application.
 */
@Configuration
public class AppConfig {

    @Bean
    public SystemConfigManager systemConfigManager() {
        return new SystemConfigManager();
    }

    @Bean
    public CodeDictManager codeDictManager() {
        return new CodeDictManager();
    }
}
