package com.cresensolutions.leaveservice.config;

import com.cresensolutions.leaveservice.common.LeaveConstants;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "leaveTaskExecutor")
    public Executor leaveTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("leave-async-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    @Bean(name = "chatbotModelExecutor", destroyMethod = "shutdown")
    public ExecutorService chatbotModelExecutor() {
        return Executors.newFixedThreadPool(LeaveConstants.CHATBOT_MODEL_EXECUTOR_THREADS);
    }
}
