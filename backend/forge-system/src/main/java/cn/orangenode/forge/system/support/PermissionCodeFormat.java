package cn.orangenode.forge.system.support;

/**
 * 权限代码格式。
 *
 * <p>权限代码是 {@code @PreAuthorize} 的唯一依据，格式固定为 {@code 模块:资源:动作}，
 * 只使用小写字母、数字、下划线与连字符。格式规则集中在此处：
 * 数据库迁移种子、创建校验与保存前的规范化引用同一份定义，避免出现两套写法。</p>
 */
public final class PermissionCodeFormat {

    /**
     * 权限代码的正则表达式，三段的字符集与长度上限一致。
     */
    public static final String PATTERN = "^[a-z0-9_-]{1,32}:[a-z0-9_-]{1,32}:[a-z0-9_-]{1,32}$";

    /**
     * 权限代码格式说明，用于校验失败提示与文档。
     */
    public static final String DESCRIPTION = "权限代码格式为 模块:资源:动作，只允许小写字母、数字、下划线与连字符";

    /**
     * 工具类不允许实例化。
     */
    private PermissionCodeFormat() {
    }

    /**
     * 判断权限代码是否符合既定格式。
     *
     * @param code 权限代码，允许为 {@code null}
     * @return 符合格式时返回 {@code true}
     */
    public static boolean isValid(String code) {
        return code != null && code.matches(PATTERN);
    }

    /**
     * 规范化权限代码：去除首尾空白。
     *
     * <p>不做大小写转换：大小写不属于权限代码的口径，格式校验会直接拒绝大写写法，
     * 避免一边接受大写一边保存小写造成“同一个代码两种写法”。</p>
     *
     * @param code 原始权限代码，允许为 {@code null}
     * @return 去空白后的权限代码，入参为空或无内容时返回 {@code null}
     */
    public static String normalize(String code) {
        return TextValues.trimToNull(code);
    }
}
