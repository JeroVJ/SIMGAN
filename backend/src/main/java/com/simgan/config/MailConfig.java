package com.simgan.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Properties;

@Configuration
public class MailConfig {

    @Bean
    @ConditionalOnMissingBean(JavaMailSender.class)
    @ConditionalOnProperty(name = "MAIL_ENABLED", havingValue = "true")
    public JavaMailSender javaMailSender(
            @Value("${MAIL_HOST}") String host,
            @Value("${MAIL_PORT:587}") int port,
            @Value("${MAIL_USERNAME}") String username,
            @Value("${MAIL_PASSWORD}") String password,
            @Value("${MAIL_PROTOCOL:smtp}") String protocol,
            @Value("${MAIL_SMTP_AUTH:true}") boolean smtpAuth,
            @Value("${MAIL_SMTP_STARTTLS_ENABLE:true}") boolean startTls,
            @Value("${MAIL_SMTP_CONNECTION_TIMEOUT:5000}") int connectionTimeout,
            @Value("${MAIL_SMTP_TIMEOUT:5000}") int timeout,
            @Value("${MAIL_SMTP_WRITE_TIMEOUT:5000}") int writeTimeout
    ) {
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost(host);
        mailSender.setPort(port);
        mailSender.setUsername(username);
        mailSender.setPassword(password);
        mailSender.setProtocol(protocol);

        Properties props = mailSender.getJavaMailProperties();
        props.put("mail.smtp.auth", Boolean.toString(smtpAuth));
        props.put("mail.smtp.starttls.enable", Boolean.toString(startTls));
        props.put("mail.smtp.connectiontimeout", Integer.toString(connectionTimeout));
        props.put("mail.smtp.timeout", Integer.toString(timeout));
        props.put("mail.smtp.writetimeout", Integer.toString(writeTimeout));

        return mailSender;
    }
}