package com.course.notification.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/** The broker address comes from an environment variable and defaults to localhost for the IDE. */
class MessagingConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer());

    @Test
    void withoutTheVariable_theIdeDefaultIsLocalhost() {
        runner.run(context -> {
            assertThat(context.getEnvironment().getProperty("spring.rabbitmq.host")).isEqualTo("localhost");
            assertThat(context.getEnvironment().getProperty("spring.rabbitmq.port")).isEqualTo("5672");
        });
    }

    @Test
    void withRabbitmqHost_theComposeServiceNameIsUsed() {
        runner.withPropertyValues("RABBITMQ_HOST=rabbitmq").run(context ->
                assertThat(context.getEnvironment().getProperty("spring.rabbitmq.host")).isEqualTo("rabbitmq"));
    }
}
