package com.serviceflow;

import com.serviceflow.config.ServiceFlowProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableConfigurationProperties(ServiceFlowProperties.class)
@EnableScheduling
public class ServiceFlowApplication {
    public static void main(String[] args) {
        SpringApplication.run(ServiceFlowApplication.class, args);
    }
}
