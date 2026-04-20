package com.cresensolutions.leaveservice.service.Impl;

import com.cresensolutions.leaveservice.model.EmailConfiguration;
import com.cresensolutions.leaveservice.repository.EmailConfigurationRepository;
import com.cresensolutions.leaveservice.service.EmailConfigurationService;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.Properties;

@Service
public class EmailConfigurationServiceImpl implements EmailConfigurationService {

    private final EmailConfigurationRepository emailConfigurationRepository;

    public EmailConfigurationServiceImpl(EmailConfigurationRepository emailConfigurationRepository) {
        this.emailConfigurationRepository = emailConfigurationRepository;
    }

    @Override
    public Optional<EmailConfiguration> getActiveConfiguration() {
        return emailConfigurationRepository.findFirstByActiveTrueOrderByIdAsc();
    }

    @Override
    public Optional<JavaMailSenderImpl> buildMailSender() {
        return getActiveConfiguration().map(this::toMailSender);
    }

    private JavaMailSenderImpl toMailSender(EmailConfiguration config) {
        String protocol = config.getProtocol() == null || config.getProtocol().isBlank()
                ? "smtp"
                : config.getProtocol();

        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost(config.getHost());
        mailSender.setPort(config.getPort() == null ? 25 : config.getPort());
        mailSender.setUsername(config.getUsername());
        mailSender.setPassword(config.getPassword());
        mailSender.setProtocol(protocol);

        Properties props = mailSender.getJavaMailProperties();
        props.put("mail.transport.protocol", protocol);
        props.put("mail.smtp.auth", Boolean.toString(config.isAuth()));
        props.put("mail.smtp.starttls.enable", Boolean.toString(config.isStarttlsEnabled()));
        props.put("mail.smtp.ssl.enable", Boolean.toString(config.isSslEnabled()));

        return mailSender;
    }
}
