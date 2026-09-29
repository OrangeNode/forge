package cn.orangenode.forge.system.support;

/**
 * 字符串判定工具。
 *
 * <p>筛选条件、可选字段与可选路由标识都按同一套“是否有内容”的判定处理，
 * 避免在多个 Service 里重复写 {@code value == null || value.isBlank()} 造成规则漂移。</p>
 */
public final class TextValues {

    /**
     * 工具类不允许实例化。
     */
    private TextValues() {
    }

    /**
     * 判断字符串是否有实际内容。
     *
     * @param value 待判断字符串，允许为 {@code null}
     * @return 非空且包含非空白字符时返回 {@code true}
     */
    public static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /**
     * 去除首尾空白，空白内容统一归一为 {@code null}。
     *
     * <p>用于可选字段：前端提交空字符串与提交 {@code null} 视为同一语义，
     * 数据库里不会出现“空字符串”与“NULL”两种空值。</p>
     *
     * @param value 原始字符串，允许为 {@code null}
     * @return 去空白后的内容，入参为空或无内容时返回 {@code null}
     */
    public static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
