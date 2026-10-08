package cn.orangenode.forge.member.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * 普通用户业务配置，唯一默认来源为 application.yaml 的 {@code forge.member.code} 与 {@code forge.member.mail}。
 *
 * <p>令牌有效期等认证参数位于基础层的 {@code forge.member.auth}；发信凭据只从环境变量读取，
 * 未配置发信服务器时发送验证码明确失败，不假装成功。</p>
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "forge.member")
public class MemberProperties {

    /**
     * 邮箱验证码参数。
     */
    @Valid
    private Code code = new Code();

    /**
     * 发信服务器参数。
     */
    @Valid
    private Mail mail = new Mail();

    /**
     * 邮箱验证码参数。
     */
    @Getter
    @Setter
    public static class Code {

        /**
         * 验证码位数。
         */
        @Min(4)
        @Max(8)
        private int length = 6;

        /**
         * 验证码有效秒数。
         */
        @Min(60)
        private long ttlSeconds = 600;

        /**
         * 同一邮箱同一用途两次发送的最短间隔秒数。
         */
        @Min(1)
        private long resendIntervalSeconds = 60;

        /**
         * 单个验证码允许的错误尝试次数，达到后验证码作废。
         */
        @Min(1)
        private int maxAttempts = 5;

        /**
         * 同一邮箱每天最多发送次数。
         */
        @Min(1)
        private int emailDailyLimit = 10;

        /**
         * 同一来源地址每天最多发送次数。
         */
        @Min(1)
        private int ipDailyLimit = 50;
    }

    /**
     * 发信服务器参数；{@code host} 为空表示未配置发信服务。
     */
    @Getter
    @Setter
    public static class Mail {

        /**
         * SMTP 服务器地址，为空表示未配置。
         */
        private String host;

        /**
         * SMTP 端口。
         */
        @Min(1)
        @Max(65535)
        private int port = 465;

        /**
         * SMTP 登录账号。
         */
        private String username;

        /**
         * SMTP 登录密码或授权码，只从环境变量读取，不打印。
         */
        private String password;

        /**
         * 发件人地址，为空时使用登录账号。
         */
        private String from;

        /**
         * 是否使用 SSL 直连（465 端口通常为 true，587 端口通常为 false 并启用 STARTTLS）。
         */
        private boolean ssl = true;

        /**
         * 连接与读写超时毫秒数。
         */
        @Min(1000)
        private int timeoutMillis = 10000;

        /**
         * 邮件主题。
         */
        @NotBlank
        private String subject = "验证码";
    }
}
