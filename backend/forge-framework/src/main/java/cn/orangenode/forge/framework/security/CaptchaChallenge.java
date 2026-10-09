package cn.orangenode.forge.framework.security;

/**
 * 一次签发的图形验证码。
 *
 * <p>答案不在此对象中，只保存在 Redis；图片为 PNG 字节，由接口层转为 data URL 返回。</p>
 *
 * @param captchaId 验证码编号，登录时与用户输入一起提交
 * @param image     PNG 图片字节
 * @param expiresIn 有效秒数
 */
public record CaptchaChallenge(String captchaId, byte[] image, long expiresIn) {
}
