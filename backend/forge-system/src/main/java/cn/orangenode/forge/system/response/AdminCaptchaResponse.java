package cn.orangenode.forge.system.response;

/**
 * 管理员登录图形验证码响应。
 *
 * <p>答案不在响应中；关闭验证码时 {@code enabled=false}，其余字段为空，前端据此隐藏验证码输入框。</p>
 *
 * @param enabled   是否要求填写验证码
 * @param captchaId 验证码编号，登录时原样提交
 * @param image     PNG 图片的 data URL
 * @param expiresIn 有效秒数
 */
public record AdminCaptchaResponse(boolean enabled, String captchaId, String image, Long expiresIn) {
}
