package com.serviceflow.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.serviceflow.service.RefundService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class RefundSandboxDisabledTest {
    @Test
    void mutationEndpointsAreAbsentUnlessExplicitlyEnabled() {
        new ApplicationContextRunner()
                .withUserConfiguration(RefundSandboxController.class)
                .withBean(RefundService.class, () -> mock(RefundService.class))
                .run(context -> assertThat(context).doesNotHaveBean(RefundSandboxController.class));
    }
}
