package com.billiard.auth;

import com.billiard.users.User;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class AuthMailService {

    private final JavaMailSender mailSender;
    private final AuthProperties authProperties;

    public AuthMailService(JavaMailSender mailSender, AuthProperties authProperties) {
        this.mailSender = mailSender;
        this.authProperties = authProperties;
    }

    public void sendPasswordReset(User user, String rawToken) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(authProperties.getMailFrom());
        message.setTo(user.getEmail());
        message.setSubject("Reset your billiard shop password");
        message.setText("""
                Hello %s,

                Use the link below to reset your password:
                %s/reset-password?token=%s

                If you did not request this, you can ignore this email.
                """.formatted(user.getFullName(), authProperties.getFrontendBaseUrl(), rawToken));
        mailSender.send(message);
    }
}
