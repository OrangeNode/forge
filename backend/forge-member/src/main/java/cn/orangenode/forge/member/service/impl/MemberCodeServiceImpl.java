package cn.orangenode.forge.member.service.impl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HexFormat;

import org.springframework.stereotype.Service;

import cn.orangenode.forge.core.exception.BusinessException;
import cn.orangenode.forge.core.exception.DependencyUnavailableException;
import cn.orangenode.forge.core.response.ErrorCode;
import cn.orangenode.forge.framework.redis.ForgeRedisTemplate;
import cn.orangenode.forge.member.config.MemberProperties;
import cn.orangenode.forge.member.mail.MemberCodePurpose;
import cn.orangenode.forge.member.mail.MemberMailSender;
import cn.orangenode.forge.member.service.MemberCodeService;
import cn.orangenode.forge.member.support.MemberValues;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 邮箱验证码实现。
 *
 * <p>键名只包含邮箱摘要，验证码只保存“邮箱 + 用途 + 验证码”的 SHA-256 摘要；
 * 日志只记录遮蔽邮箱，不记录验证码。发送失败时撤回本次验证码与冷却标记，允许立即重试。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemberCodeServiceImpl implements MemberCodeService {
    private static final String CODE_KEY = "member:code:";
    private static final String ATTEMPT_KEY = "member:code-attempt:";
    private static final String COOLDOWN_KEY = "member:code-cooldown:";
    private static final String EMAIL_DAILY_KEY = "member:code-daily:email:";
    private static final String IP_DAILY_KEY = "member:code-daily:ip:";
    private static final Duration DAY = Duration.ofDays(1);
    private final ForgeRedisTemplate redis;
    private final MemberProperties properties;
    private final MemberMailSender mailSender;
    private final SecureRandom random = new SecureRandom();

    /**
     * 检查发信配置与频率限制后发送验证码。
     */
    @Override
    public void send(String email, MemberCodePurpose purpose, String clientIp) {
        if (!mailSender.configured()) {
            throw new DependencyUnavailableException("邮件发送服务尚未配置，暂时无法发送验证码");
        }
        MemberProperties.Code rule = properties.getCode();
        String scope = scope(email, purpose);
        if (redis.hasKey(COOLDOWN_KEY + scope)) {
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS, "验证码发送过于频繁，请稍后再试");
        }
        String today = LocalDate.now(ZoneOffset.UTC).toString();
        if (redis.increment(EMAIL_DAILY_KEY + today + ":" + digest(email), DAY) > rule.getEmailDailyLimit()) {
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS, "该邮箱今日验证码次数已用完");
        }
        if (clientIp != null && redis.increment(IP_DAILY_KEY + today + ":" + clientIp, DAY) > rule.getIpDailyLimit()) {
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS, "当前网络今日验证码次数已用完");
        }
        String code = newCode(rule.getLength());
        Duration ttl = Duration.ofSeconds(rule.getTtlSeconds());
        redis.set(CODE_KEY + scope, digest(scope + ":" + code), ttl);
        redis.delete(ATTEMPT_KEY + scope);
        redis.set(COOLDOWN_KEY + scope, "1", Duration.ofSeconds(rule.getResendIntervalSeconds()));
        try {
            mailSender.sendCode(email, purpose, code);
        } catch (RuntimeException failure) {
            redis.delete(CODE_KEY + scope);
            redis.delete(COOLDOWN_KEY + scope);
            throw failure;
        }
        log.info("已发送{}验证码，收件人={}", purpose.label(), MemberValues.mask(email));
    }

    /**
     * 比较验证码摘要，正确后删除验证码使其只能使用一次。
     */
    @Override
    public void consume(String email, MemberCodePurpose purpose, String code) {
        String scope = scope(email, purpose);
        String expected = redis.get(CODE_KEY + scope);
        if (expected == null || code == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "验证码错误或已失效");
        }
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                digest(scope + ":" + code.strip()).getBytes(StandardCharsets.UTF_8))) {
            long attempts = redis.increment(ATTEMPT_KEY + scope,
                    Duration.ofSeconds(properties.getCode().getTtlSeconds()));
            if (attempts >= properties.getCode().getMaxAttempts()) {
                redis.delete(CODE_KEY + scope);
                redis.delete(ATTEMPT_KEY + scope);
            }
            throw new BusinessException(ErrorCode.BAD_REQUEST, "验证码错误或已失效");
        }
        redis.delete(CODE_KEY + scope);
        redis.delete(ATTEMPT_KEY + scope);
    }

    /**
     * 生成定长数字验证码。
     */
    private String newCode(int length) {
        StringBuilder builder = new StringBuilder(length);
        for (int index = 0; index < length; index++) {
            builder.append(random.nextInt(10));
        }
        return builder.toString();
    }

    /**
     * 计算邮箱与用途组成的键名片段。
     */
    private String scope(String email, MemberCodePurpose purpose) {
        return purpose.name() + ":" + digest(email);
    }

    /**
     * 计算 SHA-256 摘要。
     */
    private static String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("运行环境缺少 SHA-256 摘要算法", failure);
        }
    }
}
