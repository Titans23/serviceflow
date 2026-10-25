package com.serviceflow.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.reactive.function.client.WebClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "serviceflow.evaluation.provider-budget-enabled", havingValue = "true")
public class EvaluationProviderBudgetConfiguration {
    @Bean
    EvaluationProviderBudget evaluationProviderBudget(Environment environment, ServiceFlowProperties properties) {
        return new EvaluationProviderBudget(environment, properties);
    }

    @Bean
    WebClientCustomizer evaluationProviderWebClientCustomizer(EvaluationProviderBudget budget) {
        return budget::customize;
    }
}
