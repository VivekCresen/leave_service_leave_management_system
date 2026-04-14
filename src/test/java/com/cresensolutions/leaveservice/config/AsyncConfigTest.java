package com.cresensolutions.leaveservice.config;

import org.junit.jupiter.api.Test;

import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;

class AsyncConfigTest {

    @Test
    void leaveTaskExecutor_returnsConfiguredExecutor() {
        AsyncConfig config = new AsyncConfig();
        Executor executor = config.leaveTaskExecutor();

        assertThat(executor).isNotNull();
    }
}
