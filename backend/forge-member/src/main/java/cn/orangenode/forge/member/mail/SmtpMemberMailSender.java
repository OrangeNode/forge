package cn.orangenode.forge.member.mail;

import java.util.Properties;

import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;

import cn.orangenode.forge.core.exception.DependencyUnavailableException;
import cn.orangenode.forge.member.config.MemberProperties;
import cn.orangenode.forge.member.support.MemberValues;

import lombok.extern.slf4j.Slf4j;

/**
 * SMTP 验证码邮件发送。
 *
 * <p>发信服务器只来自 {@code forge.member.mail.*}（凭据来自环境变量）；未配置主机时不创建发信客户端，
 * 由验证码用例返回“发信服务未配置”。日志只记录遮蔽后的邮箱，不记录验证码与凭据。</p>
 */
@Slf4j
@Component
public class SmtpMemberMailSender implements MemberMailSender {

    /**
     * 发信服务器参数。
     */
    private final MemberProperties.Mail mail;

    /**
     * 发信客户端，未配置时为 {@code null}。
     */
    private final JavaMailSenderImpl client;

    /**
     * 构造 SMTP 发送器，按配置创建发信客户端。
     *
     * @param properties 普通用户业务配置
     */
    public SmtpMemberMailSender(MemberProperties properties) {
        this.mail = properties.getMail();
        this.client = MemberValues.clean(mail.getHost()) == null ? null : createClient(mail);
        log.info("验证码发信服务{}", client == null ? "未配置，发送验证码将返回明确错误" : "已配置，主机 " + mail.getHost());
    }

    /**
     * 判断发信服务是否已配置。
     *
     * @return 已配置时为 {@code true}
     */
    @Override
    public boolean configured() {
        return client != null;
    }

    /**
     * 发送验证码邮件，失败按依赖不可用处理。
     *
     * @param email   收件邮箱
     * @param purpose 验证码用途
     * @param code    验证码
     */
    @Override
    public void sendCode(String email, MemberCodePurpose purpose, String code) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(MemberValues.clean(mail.getFrom()) == null ? mail.getUsername() : mail.getFrom());
        message.setTo(email);
        message.setSubject(mail.getSubject());
        message.setText("您正在" + purpose.label() + "，验证码为：" + code + "\n验证码几分钟内有效，请勿告诉他人。如非本人操作请忽略本邮件。");
        try {
            client.send(message);
        } catch (MailException failure) {
            log.error("验证码邮件发送失败，收件人={}，原因={}", MemberValues.mask(email), failure.getClass().getSimpleName());
            throw new DependencyUnavailableException("验证码邮件发送失败，请稍后重试", failure);
        }
    }

    /**
     * 创建发信客户端。
     *
     * @param mail 发信服务器参数
     * @return 发信客户端
     */
    private static JavaMailSenderImpl createClient(MemberProperties.Mail mail) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(mail.getHost().strip());
        sender.setPort(mail.getPort());
        sender.setUsername(mail.getUsername());
        sender.setPassword(mail.getPassword());
        sender.setDefaultEncoding("UTF-8");
        Properties javaMail = sender.getJavaMailProperties();
        javaMail.put("mail.smtp.auth", String.valueOf(MemberValues.clean(mail.getUsername()) != null));
        javaMail.put("mail.smtp.ssl.enable", String.valueOf(mail.isSsl()));
        javaMail.put("mail.smtp.starttls.enable", String.valueOf(!mail.isSsl()));
        javaMail.put("mail.smtp.connectiontimeout", String.valueOf(mail.getTimeoutMillis()));
        javaMail.put("mail.smtp.timeout", String.valueOf(mail.getTimeoutMillis()));
        javaMail.put("mail.smtp.writetimeout", String.valueOf(mail.getTimeoutMillis()));
        return sender;
    }
}
