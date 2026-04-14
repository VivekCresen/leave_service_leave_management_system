package com.cresensolutions.leaveservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(
        excludeName = {
                "org.flowable.spring.boot.eventregistry.EventRegistryAutoConfiguration",
                "org.flowable.spring.boot.FlowableSecurityAutoConfiguration"
        }
)
@EnableScheduling
public class LeaveServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(LeaveServiceApplication.class, args);
    }

}
