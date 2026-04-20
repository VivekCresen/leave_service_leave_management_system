package com.cresensolutions.leaveservice.service;

import com.cresensolutions.leaveservice.model.EmailConfiguration;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Optional;

public interface EmailConfigurationService {

    Optional<EmailConfiguration> getActiveConfiguration();

    Optional<JavaMailSenderImpl> buildMailSender();
}
