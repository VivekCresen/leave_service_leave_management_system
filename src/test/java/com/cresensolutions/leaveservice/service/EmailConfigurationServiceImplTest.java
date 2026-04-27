package com.cresensolutions.leaveservice.service;

import com.cresensolutions.leaveservice.model.EmailConfiguration;
import com.cresensolutions.leaveservice.repository.EmailConfigurationRepository;
import com.cresensolutions.leaveservice.service.Impl.EmailConfigurationServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailConfigurationServiceImplTest {

    @Mock private EmailConfigurationRepository emailConfigurationRepository;
    @InjectMocks private EmailConfigurationServiceImpl emailConfigurationService;

    @Test
    void getActiveConfiguration_returnsActiveConfig() {
        EmailConfiguration config = buildConfig("smtp.test.com", 587, "smtp", true, true, false);
        when(emailConfigurationRepository.findFirstByActiveTrueOrderByIdAsc())
                .thenReturn(Optional.of(config));

        Optional<EmailConfiguration> result = emailConfigurationService.getActiveConfiguration();

        assertThat(result).isPresent();
        assertThat(result.get().getHost()).isEqualTo("smtp.test.com");
    }

    @Test
    void getActiveConfiguration_noConfig_returnsEmpty() {
        when(emailConfigurationRepository.findFirstByActiveTrueOrderByIdAsc())
                .thenReturn(Optional.empty());

        Optional<EmailConfiguration> result = emailConfigurationService.getActiveConfiguration();

        assertThat(result).isEmpty();
    }

    @Test
    void buildMailSender_withActiveConfig_returnsMailSender() {
        EmailConfiguration config = buildConfig("smtp.test.com", 587, "smtp", true, true, false);
        when(emailConfigurationRepository.findFirstByActiveTrueOrderByIdAsc())
                .thenReturn(Optional.of(config));

        Optional<JavaMailSenderImpl> result = emailConfigurationService.buildMailSender();

        assertThat(result).isPresent();
        assertThat(result.get().getHost()).isEqualTo("smtp.test.com");
        assertThat(result.get().getPort()).isEqualTo(587);
        assertThat(result.get().getProtocol()).isEqualTo("smtp");
    }

    @Test
    void buildMailSender_noActiveConfig_returnsEmpty() {
        when(emailConfigurationRepository.findFirstByActiveTrueOrderByIdAsc())
                .thenReturn(Optional.empty());

        Optional<JavaMailSenderImpl> result = emailConfigurationService.buildMailSender();

        assertThat(result).isEmpty();
    }

    @Test
    void buildMailSender_nullProtocol_defaultsToSmtp() {
        EmailConfiguration config = buildConfig("smtp.test.com", 25, null, false, false, false);
        when(emailConfigurationRepository.findFirstByActiveTrueOrderByIdAsc())
                .thenReturn(Optional.of(config));

        Optional<JavaMailSenderImpl> result = emailConfigurationService.buildMailSender();

        assertThat(result).isPresent();
        assertThat(result.get().getProtocol()).isEqualTo("smtp");
    }

    @Test
    void buildMailSender_blankProtocol_defaultsToSmtp() {
        EmailConfiguration config = buildConfig("smtp.test.com", 25, "  ", false, false, false);
        when(emailConfigurationRepository.findFirstByActiveTrueOrderByIdAsc())
                .thenReturn(Optional.of(config));

        Optional<JavaMailSenderImpl> result = emailConfigurationService.buildMailSender();

        assertThat(result).isPresent();
        assertThat(result.get().getProtocol()).isEqualTo("smtp");
    }

    @Test
    void buildMailSender_nullPort_defaultsTo25() {
        EmailConfiguration config = buildConfig("smtp.test.com", null, "smtp", false, false, false);
        when(emailConfigurationRepository.findFirstByActiveTrueOrderByIdAsc())
                .thenReturn(Optional.of(config));

        Optional<JavaMailSenderImpl> result = emailConfigurationService.buildMailSender();

        assertThat(result).isPresent();
        assertThat(result.get().getPort()).isEqualTo(25);
    }

    @Test
    void buildMailSender_sslEnabled_setsProperties() {
        EmailConfiguration config = buildConfig("smtp.test.com", 465, "smtp", true, false, true);
        when(emailConfigurationRepository.findFirstByActiveTrueOrderByIdAsc())
                .thenReturn(Optional.of(config));

        Optional<JavaMailSenderImpl> result = emailConfigurationService.buildMailSender();

        assertThat(result).isPresent();
        assertThat(result.get().getJavaMailProperties().getProperty("mail.smtp.ssl.enable"))
                .isEqualTo("true");
    }

    @Test
    void buildMailSender_starttlsEnabled_setsProperties() {
        EmailConfiguration config = buildConfig("smtp.test.com", 587, "smtp", true, true, false);
        when(emailConfigurationRepository.findFirstByActiveTrueOrderByIdAsc())
                .thenReturn(Optional.of(config));

        Optional<JavaMailSenderImpl> result = emailConfigurationService.buildMailSender();

        assertThat(result).isPresent();
        assertThat(result.get().getJavaMailProperties().getProperty("mail.smtp.starttls.enable"))
                .isEqualTo("true");
    }

    @Test
    void buildMailSender_authEnabled_setsProperties() {
        EmailConfiguration config = buildConfig("smtp.test.com", 587, "smtp", true, false, false);
        when(emailConfigurationRepository.findFirstByActiveTrueOrderByIdAsc())
                .thenReturn(Optional.of(config));

        Optional<JavaMailSenderImpl> result = emailConfigurationService.buildMailSender();

        assertThat(result).isPresent();
        assertThat(result.get().getJavaMailProperties().getProperty("mail.smtp.auth"))
                .isEqualTo("true");
    }

    private static EmailConfiguration buildConfig(String host, Integer port, String protocol,
                                                   boolean auth, boolean starttls, boolean ssl) {
        try {
            Constructor<EmailConfiguration> ctor =
                    EmailConfiguration.class.getDeclaredConstructor();
            ctor.setAccessible(true);
            EmailConfiguration config = ctor.newInstance();
            setField(config, "host", host);
            setField(config, "port", port);
            setField(config, "protocol", protocol);
            setField(config, "auth", auth);
            setField(config, "starttlsEnabled", starttls);
            setField(config, "sslEnabled", ssl);
            setField(config, "username", "user@test.com");
            setField(config, "password", "secret");
            setField(config, "fromAddress", "noreply@test.com");
            setField(config, "active", true);
            return config;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Class<?> clazz = target.getClass();
        while (clazz != null) {
            try {
                Field f = clazz.getDeclaredField(fieldName);
                f.setAccessible(true);
                f.set(target, value);
                return;
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            }
        }
        throw new NoSuchFieldException(fieldName);
    }
}
