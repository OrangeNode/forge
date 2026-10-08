package cn.orangenode.forge.app.support;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import cn.orangenode.forge.core.exception.BusinessException;
import cn.orangenode.forge.core.response.ErrorCode;

/**
 * App 管理中的标识、时间与图片地址规则。
 */
public final class AppValues {

    /**
     * App 图片读取接口路径前缀，与公开接口的映射保持一致；地址不含域名，由客户端拼接接口基础地址。
     */
    public static final String IMAGE_PATH = "/api/app/v1/app/images/";

    /**
     * 工具类不允许实例化。
     */
    private AppValues() {
    }

    /**
     * 校验正数主键字符串。
     *
     * @param value 主键字符串
     * @param label 字段中文名
     * @return 主键
     */
    public static Long id(String value, String label) {
        try {
            long result = Long.parseLong(value.strip());
            if (result <= 0) {
                throw new NumberFormatException();
            }
            return result;
        } catch (NumberFormatException | NullPointerException failure) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, label + "必须是有效的正整数字符串");
        }
    }

    /**
     * 可空主键字符串转为主键。
     *
     * @param value 主键字符串
     * @param label 字段中文名
     * @return 主键，空值返回 {@code null}
     */
    public static Long optionalId(String value, String label) {
        return value == null || value.isBlank() ? null : id(value, label);
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
     * 时刻转为数据库 UTC 时间。
     *
     * @param value 时刻
     * @return UTC 本地时间
     */
    public static LocalDateTime utc(Instant value) {
        return value == null ? null : LocalDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    /**
     * 输出带时区的时间文本。
     *
     * @param value UTC 本地时间
     * @return ISO 8601 文本
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
     * 生成 App 图片相对地址。
     *
     * @param fileId 文件主键
     * @return 图片地址
     */
    public static String imageUrl(Long fileId) {
        return fileId == null ? null : IMAGE_PATH + fileId;
    }

    /**
     * 判断记录在给定时刻是否处于生效区间。
     *
     * @param enabled 是否启用
     * @param startAt 开始时间
     * @param endAt   结束时间
     * @param now     判断时刻
     * @return 处于生效区间时为 {@code true}
     */
    public static boolean active(Boolean enabled, LocalDateTime startAt, LocalDateTime endAt, LocalDateTime now) {
        return Boolean.TRUE.equals(enabled) && (startAt == null || !startAt.isAfter(now))
                && (endAt == null || endAt.isAfter(now));
    }

    /**
     * 校验开始时间早于结束时间。
     *
     * @param startAt 开始时间
     * @param endAt   结束时间
     */
    public static void assertWindow(Instant startAt, Instant endAt) {
        if (startAt != null && endAt != null && !endAt.isAfter(startAt)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "结束时间必须晚于开始时间");
        }
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
