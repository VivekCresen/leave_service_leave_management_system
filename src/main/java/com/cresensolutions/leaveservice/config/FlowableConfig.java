package com.cresensolutions.leaveservice.config;

import org.flowable.spring.SpringProcessEngineConfiguration;
import org.flowable.spring.boot.EngineConfigurationConfigurer;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;


@Configuration
public class FlowableConfig {

    private final ApplicationContext applicationContext;

    public FlowableConfig(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Bean
    public EngineConfigurationConfigurer<SpringProcessEngineConfiguration> flowableEngineConfigurer() {
        return config -> {
        
            Resource[] deploymentResources = new Resource[]{
                new ClassPathResource("processes/Leave_Approval_Process.bpmn20.xml")
            };
            config.setDeploymentResources(deploymentResources);
            config.setApplicationContext(applicationContext);
            config.setAsyncExecutorActivate(true);
        };
    }
}
