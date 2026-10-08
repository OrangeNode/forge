package cn.orangenode.forge.member.mail;

/**
 * 邮箱验证码用途；不同用途的验证码互不通用。
 */
public enum MemberCodePurpose {

    /**
     * 注册账号。
     */
    REGISTER("注册账号"),

    /**
     * 找回密码。
     */
    RESET_PASSWORD("找回密码");

    /**
     * 邮件中展示的中文用途。
     */
    private final String label;

    /**
     * 构造验证码用途。
     *
     * @param label 中文用途
     */
    MemberCodePurpose(String label) {
        this.label = label;
    }

    /**
     * 读取中文用途。
     *
     * @return 中文用途
     */
    public String label() {
        return label;
    }
}
