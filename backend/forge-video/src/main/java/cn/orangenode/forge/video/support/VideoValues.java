package cn.orangenode.forge.video.support;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import cn.orangenode.forge.core.exception.BusinessException;
import cn.orangenode.forge.core.response.ErrorCode;

/**
 * 视频业务中的标识、时间及保守文本规范化。
 */
public final class VideoValues {
    /**
     * 工具类不允许实例化。
     */
    private VideoValues() {
    }

    /**
     * 校验正数主键，拒绝溢出与非数字。
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
     * 取得当前 UTC 数据库时间。
     */
    public static LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    /**
     * 输出带时区的时间。
     */
    public static String time(LocalDateTime value) {
        return value == null ? null : value.atOffset(ZoneOffset.UTC).toString();
    }

    /**
     * 将内部主键转换为外部字符串。
     */
    public static String textId(Long value) {
        return value == null ? null : value.toString();
    }

    /**
     * 去除首尾空白，空文本转为空值。
     */
    public static String clean(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    /**
     * 只统一全半角、空白和大小写，不移除季数或片名词语。
     */
    public static String normalize(String value) {
        return value == null ? "" : Normalizer.normalize(value, Normalizer.Form.NFKC)
                .strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    /**
     * 按完整人员集合比较导演与演员，不使用模糊匹配。
     */
    public static String people(String value) {
        return Arrays.stream(normalize(value).split("[,，、;/；]"))
                .map(String::strip).filter(s -> !s.isEmpty()).distinct().sorted()
                .collect(java.util.stream.Collectors.joining(","));
    }

    /**
     * 拆分数据库中的条件代码。
     */
    public static List<String> fields(String value) {
        return value == null || value.isBlank() ? List.of() : List.of(value.split(","));
    }

    /**
     * 比较两个可空业务值。
     */
    public static boolean same(Object left, Object right) {
        return Objects.equals(left, right);
    }
}
