package com.cresensolutions.leaveservice.common;

import com.cresensolutions.leaveservice.model.EmailConfiguration;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Properties;

public final class MailSenderFactory {

    private MailSenderFactory() {}

    public static JavaMailSenderImpl build(EmailConfiguration config) {
        String protocol = StringUtils.trimOrNull(config.getProtocol());
        if (protocol == null) protocol = "smtp";

        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(config.getHost());
        sender.setPort(config.getPort() == null ? 25 : config.getPort());
        sender.setUsername(config.getUsername());
        sender.setPassword(config.getPassword());
        sender.setProtocol(protocol);

        Properties props = sender.getJavaMailProperties();
        props.put("mail.transport.protocol", protocol);
        props.put("mail.smtp.auth", Boolean.toString(config.isAuth()));
        props.put("mail.smtp.starttls.enable", Boolean.toString(config.isStarttlsEnabled()));
        props.put("mail.smtp.ssl.enable", Boolean.toString(config.isSslEnabled()));

        return sender;
    }
}
