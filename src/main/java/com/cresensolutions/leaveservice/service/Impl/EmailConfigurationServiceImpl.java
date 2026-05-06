package com.cresensolutions.leaveservice.service.Impl;

import com.cresensolutions.leaveservice.common.MailSenderFactory;
import com.cresensolutions.leaveservice.model.EmailConfiguration;
import com.cresensolutions.leaveservice.repository.EmailConfigurationRepository;
import com.cresensolutions.leaveservice.service.EmailConfigurationService;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@Transactional(readOnly = true)
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
        return getActiveConfiguration().map(MailSenderFactory::build);
    }
}
