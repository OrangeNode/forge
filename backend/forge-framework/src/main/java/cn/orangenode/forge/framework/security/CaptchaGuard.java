package cn.orangenode.forge.framework.security;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import cn.orangenode.forge.core.exception.BusinessException;
import cn.orangenode.forge.core.response.ErrorCode;
import cn.orangenode.forge.framework.config.ForgeSecurityProperties;
import cn.orangenode.forge.framework.redis.ForgeRedisTemplate;

import lombok.extern.slf4j.Slf4j;

/**
 * 管理员登录图形验证码。
 *
 * <p>签发时生成随机编号与答案，答案只写入 Redis 并设置有效期；校验时先删除再比较，
 * 无论对错一个验证码只能使用一次，防止对同一张图片反复猜测。比较不区分大小写。</p>
 *
 * <p>字符集去掉了容易混淆的 0/O、1/I/L，降低人工输入出错。</p>
 */
@Slf4j
@Component
public class CaptchaGuard {

    /**
     * 验证码答案键的逻辑前缀。
     */
    private static final String KEY_PREFIX = "auth:captcha:";

    /**
     * 验证码字符集。
     */
    private static final String ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";

    /**
     * 验证码编号的随机字节数。
     */
    private static final int ID_BYTES = 16;

    /**
     * 合法验证码编号：32 位小写十六进制。
     */
    private static final Pattern ID_PATTERN = Pattern.compile("^[0-9a-f]{32}$");

    /**
     * 验证码错误、过期或缺失时的统一提示。
     */
    private static final String INVALID_MESSAGE = "验证码错误或已过期，请重新输入";

    /**
     * Redis 操作入口。
     */
    private final ForgeRedisTemplate redis;

    /**
     * 认证与权限配置，提供验证码参数。
     */
    private final ForgeSecurityProperties properties;

    /**
     * 图片绘制器。
     */
    private final CaptchaImageRenderer renderer = new CaptchaImageRenderer();

    /**
     * 随机源。
     */
    private final SecureRandom random = new SecureRandom();

    /**
     * 构造图形验证码组件。
     *
     * @param redis      Redis 操作入口
     * @param properties 认证与权限配置
     */
    public CaptchaGuard(ForgeRedisTemplate redis, ForgeSecurityProperties properties) {
        this.redis = redis;
        this.properties = properties;
    }

    /**
     * 是否要求登录填写验证码。
     *
     * @return 开启时为 {@code true}
     */
    public boolean isEnabled() {
        return properties.getCaptcha().isEnabled();
    }

    /**
     * 签发一个新的验证码，答案写入 Redis。
     *
     * @return 验证码编号、图片与有效期
     */
    public CaptchaChallenge issue() {
        ForgeSecurityProperties.Captcha config = properties.getCaptcha();
        StringBuilder answer = new StringBuilder(config.getLength());
        for (int index = 0; index < config.getLength(); index++) {
            answer.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        byte[] idBytes = new byte[ID_BYTES];
        random.nextBytes(idBytes);
        String captchaId = HexFormat.of().formatHex(idBytes);
        redis.set(KEY_PREFIX + captchaId, answer.toString(), Duration.ofSeconds(config.getTtlSeconds()));
        byte[] image = renderer.render(answer.toString(), config.getWidth(), config.getHeight());
        return new CaptchaChallenge(captchaId, image, config.getTtlSeconds());
    }

    /**
     * 校验并作废验证码；关闭验证码时直接通过。
     *
     * @param captchaId 验证码编号
     * @param code      用户输入
     * @throws BusinessException 验证码缺失、过期、已使用或不匹配时抛出 400
     */
    public void verify(String captchaId, String code) {
        if (!isEnabled()) {
            return;
        }
        if (captchaId == null || !ID_PATTERN.matcher(captchaId).matches() || code == null || code.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, INVALID_MESSAGE);
        }
        String key = KEY_PREFIX + captchaId;
        String expected = redis.get(key);
        // 先删除再比较：删除成功的一方才算使用了这张验证码，并发重复提交只有一次机会
        if (expected == null || redis.delete(key) == 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, INVALID_MESSAGE);
        }
        if (!expected.equals(code.strip().toUpperCase(Locale.ROOT))) {
            log.debug("验证码不匹配");
            throw new BusinessException(ErrorCode.BAD_REQUEST, INVALID_MESSAGE);
        }
    }
}
