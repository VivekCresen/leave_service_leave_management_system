package org.springframework.boot.autoconfigure.security.servlet;

import org.springframework.context.annotation.Configuration;

@Configuration
public class SecurityAutoConfiguration {

    @org.springframework.context.annotation.Bean
    public org.springframework.security.authentication.AuthenticationEventPublisher authenticationEventPublisher() {
        return new org.springframework.security.authentication.DefaultAuthenticationEventPublisher();
    }
}
