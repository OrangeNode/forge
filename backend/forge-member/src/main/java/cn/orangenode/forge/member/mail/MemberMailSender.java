package cn.orangenode.forge.member.mail;

/**
 * 验证码邮件发送端口。
 *
 * <p>生产实现使用 SMTP；测试使用替身捕获验证码，不真实发信。实现不得在日志中输出验证码。</p>
 */
public interface MemberMailSender {

    /**
     * 判断发信服务是否已配置。
     *
     * @return 已配置时为 {@code true}
     */
    boolean configured();

    /**
     * 发送验证码邮件；发送失败抛出依赖不可用异常。
     *
     * @param email   收件邮箱
     * @param purpose 验证码用途
     * @param code    验证码
     */
    void sendCode(String email, MemberCodePurpose purpose, String code);
}
