package cn.orangenode.forge.member.support;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Locale;

import cn.orangenode.forge.core.exception.BusinessException;
import cn.orangenode.forge.core.response.ErrorCode;

/**
 * 普通用户业务中的标识、时间、邮箱与密码规则。
 *
 * <p>规则集中在此类，Validation 注解与用例引用同一组常量，避免同一约束出现多个来源。</p>
 */
public final class MemberValues {

    /**
     * 密码最小长度。
     */
    public static final int PASSWORD_MIN_LENGTH = 6;

    /**
     * 密码最大长度。
     */
    public static final int PASSWORD_MAX_LENGTH = 64;

    /**
     * 昵称最大长度，与数据库列一致。
     */
    public static final int NICKNAME_MAX_LENGTH = 32;

    /**
     * 邮箱最大长度，与数据库列一致。
     */
    public static final int EMAIL_MAX_LENGTH = 128;

    /**
     * 设备编号格式：App 首次安装生成的随机编号。
     */
    public static final String DEVICE_CODE_PATTERN = "^[A-Za-z0-9_-]{8,64}$";

    /**
     * 工具类不允许实例化。
     */
    private MemberValues() {
    }

    /**
     * 规范化邮箱：去首尾空格并转为小写。
     *
     * @param email 原始邮箱
     * @return 规范化邮箱，空值返回 {@code null}
     */
    public static String email(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        return email.strip().toLowerCase(Locale.ROOT);
    }

    /**
     * 遮蔽邮箱用于日志，只保留首字符与域名。
     *
     * @param email 规范化邮箱
     * @return 遮蔽后的邮箱
     */
    public static String mask(String email) {
        if (email == null) {
            return null;
        }
        int at = email.indexOf('@');
        return at <= 0 ? "***" : email.charAt(0) + "***" + email.substring(at);
    }

    /**
     * 校验正数主键字符串。
     *
     * @param value 主键字符串
     * @return 主键
     */
    public static Long id(String value) {
        try {
            long result = Long.parseLong(value);
            if (result <= 0) {
                throw new NumberFormatException();
            }
            return result;
        } catch (NumberFormatException | NullPointerException failure) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "ID 必须是有效的正整数字符串");
        }
    }

    /**
     * 当前 UTC 数据库时间。
     *
     * @return UTC 本地时间
     */
    public static LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    /**
     * 数据库 UTC 时间转为时刻。
     *
     * @param value UTC 本地时间
     * @return 时刻，空值返回 {@code null}
     */
    public static Instant instant(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }

    /**
     * 时刻转为数据库 UTC 时间。
     *
     * @param value 时刻
     * @return UTC 本地时间，空值返回 {@code null}
     */
    public static LocalDateTime utc(Instant value) {
        return value == null ? null : LocalDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    /**
     * 输出带时区的时间文本。
     *
     * @param value UTC 本地时间
     * @return ISO 8601 文本，空值返回 {@code null}
     */
    public static String time(LocalDateTime value) {
        return value == null ? null : value.atOffset(ZoneOffset.UTC).toString();
    }

    /**
     * 主键转为对外字符串。
     *
     * @param value 主键
     * @return 字符串主键
     */
    public static String textId(Long value) {
        return value == null ? null : value.toString();
    }

    /**
     * 去除首尾空白，空文本转为空值。
     *
     * @param value 原始文本
     * @return 清理后的文本
     */
    public static String clean(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
