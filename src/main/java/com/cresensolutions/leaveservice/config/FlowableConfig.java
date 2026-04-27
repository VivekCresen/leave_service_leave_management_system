package com.cresensolutions.leaveservice.config;

import org.flowable.spring.SpringProcessEngineConfiguration;
import org.flowable.spring.boot.EngineConfigurationConfigurer;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
public class FlowableConfig {

    private final ApplicationContext applicationContext;

    public FlowableConfig(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Bean
    public EngineConfigurationConfigurer<SpringProcessEngineConfiguration> flowableEngineConfigurer() {
        return config -> {
            config.setApplicationContext(applicationContext);
            config.setAsyncExecutorActivate(true);
        };
    }
}
