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
    @ConditionalOnProperty(name = "app.mail.enabled", havingValue = "true")
    public JavaMailSender javaMailSender(
            @Value("${app.mail.host}") String host,
            @Value("${app.mail.port}") int port,
            @Value("${app.mail.username}") String username,
            @Value("${app.mail.password}") String password,
            @Value("${app.mail.protocol}") String protocol,
            @Value("${app.mail.smtp.auth}") boolean smtpAuth,
            @Value("${app.mail.smtp.starttls-enable}") boolean startTls,
            @Value("${app.mail.smtp.connection-timeout}") int connectionTimeout,
            @Value("${app.mail.smtp.timeout}") int timeout,
            @Value("${app.mail.smtp.write-timeout}") int writeTimeout
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