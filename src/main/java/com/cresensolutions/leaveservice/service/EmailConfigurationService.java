package com.cresensolutions.leaveservice.service;

import com.cresensolutions.leaveservice.model.EmailConfiguration;
import com.cresensolutions.leaveservice.repository.EmailConfigurationRepository;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.Properties;

@Service
public class EmailConfigurationService {

    private final EmailConfigurationRepository emailConfigurationRepository;

    public EmailConfigurationService(EmailConfigurationRepository emailConfigurationRepository) {
        this.emailConfigurationRepository = emailConfigurationRepository;
    }

    public Optional<EmailConfiguration> getActiveConfiguration() {
        return emailConfigurationRepository.findFirstByActiveTrueOrderByIdAsc();
    }

    public Optional<JavaMailSenderImpl> buildMailSender() {
        return getActiveConfiguration().map(this::buildMailSender);
    }

    private JavaMailSenderImpl buildMailSender(EmailConfiguration configuration) {
        String protocol = configuration.getProtocol() == null || configuration.getProtocol().isBlank()
                ? "smtp"
                : configuration.getProtocol();
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost(configuration.getHost());
        mailSender.setPort(configuration.getPort() == null ? 25 : configuration.getPort());
        mailSender.setUsername(configuration.getUsername());
        mailSender.setPassword(configuration.getPassword());
        mailSender.setProtocol(protocol);

        Properties properties = mailSender.getJavaMailProperties();
        properties.put("mail.transport.protocol", protocol);
        properties.put("mail.smtp.auth", Boolean.toString(configuration.isAuth()));
        properties.put("mail.smtp.starttls.enable", Boolean.toString(configuration.isStarttlsEnabled()));
        properties.put("mail.smtp.ssl.enable", Boolean.toString(configuration.isSslEnabled()));

        return mailSender;
    }
}