package cn.orangenode.forge.core.response;

/**
 * 参数校验失败中的单个字段错误。
 *
 * <p>只包含字段名与中文说明，不回显被拒绝的原值，避免把密码、令牌等内容写回响应。</p>
 *
 * @param field   校验失败的字段名或参数名
 * @param message 面向调用者的中文说明
 */
public record FieldError(String field, String message) {

    /**
     * 规范化字段错误，保证两个字段都是非空字符串。
     *
     * @param field   校验失败的字段名或参数名
     * @param message 面向调用者的中文说明
     */
    public FieldError {
        field = field == null ? "" : field;
        message = message == null ? "" : message;
    }
}
