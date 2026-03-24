package com.example.SqlQuiz.service;

import com.example.SqlQuiz.entity.EmailVerification;
import com.example.SqlQuiz.repository.EmailVerificationRepository;
import org.apache.commons.lang3.RandomStringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@Transactional
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    @Autowired
    private JavaMailSender mailSender;

    @Autowired
    private EmailVerificationRepository verificationRepository;

    @Value("${app.verification.code-expiry-minutes:5}")
    private int codeExpiryMinutes;

    @Value("${app.mail.from:noreply@sqlquiz.com}")
    private String fromEmail;

    /**
     * 发送验证码到指定邮箱
     */
    public String sendVerificationCode(String email) {
        log.info("[Email Service] 准备发送验证码到: {}", email);
        log.info("[Email Service] 发件人: {}", fromEmail);

        // 删除该邮箱之前的验证码
        verificationRepository.deleteByEmail(email);
        // 强制刷新，确保删除操作完成
        verificationRepository.flush();

        // 生成6位数字验证码
        String code = RandomStringUtils.randomNumeric(6);
        log.info("[Email Service] 生成的验证码: {}", code);
        System.out.println("[Email Verification Code] 邮箱 " + email + " 的验证码: " + code);

        // 保存验证码记录
        EmailVerification verification = new EmailVerification(email, code, codeExpiryMinutes);
        verificationRepository.save(verification);

        // 发送邮件
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(email);
            message.setSubject("SQL Quiz - Email Verification Code");
            message.setText("Your verification code is: " + code + "\n\n" +
                    "This code will expire in " + codeExpiryMinutes + " minutes.\n" +
                    "If you did not request this code, please ignore this email.");

            log.info("[Email Service] 开始发送邮件...");
            mailSender.send(message);
            log.info("[Email Service] 邮件发送成功!");
            return "success";
        } catch (Exception e) {
            log.error("[Email Service] 邮件发送失败!", e);
            throw new RuntimeException("Failed to send email: " + e.getMessage());
        }
    }

    /**
     * 验证验证码
     */
    public boolean verifyCode(String email, String code) {
        EmailVerification verification = verificationRepository
                .findFirstByEmailOrderByCreatedAtDesc(email)
                .orElse(null);

        if (verification == null) {
            return false;
        }

        // 检查是否过期
        if (verification.isExpired()) {
            return false;
        }

        // 检查验证码是否匹配
        if (!verification.getCode().equals(code)) {
            return false;
        }

        // 标记为已验证
        verification.setVerified(true);
        verificationRepository.save(verification);
        return true;
    }

    /**
     * 检查邮箱是否已验证（在指定时间内）
     */
    public boolean isEmailVerified(String email) {
        EmailVerification verification = verificationRepository
                .findFirstByEmailOrderByCreatedAtDesc(email)
                .orElse(null);

        if (verification == null) {
            return false;
        }

        return verification.getVerified() && !verification.isExpired();
    }

    /**
     * 验证后清理验证码
     */
    public void cleanupAfterRegistration(String email) {
        verificationRepository.deleteByEmail(email);
    }
}
