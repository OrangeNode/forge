package cn.orangenode.forge.system.request;

import cn.orangenode.forge.system.credential.AdminCredentialPolicy;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 管理员密码重置入参。
 *
 * <p>只接收新密码明文，按创建密码规则校验长度；保存前编码，接口与日志都不回显密码。
 * 重置成功后该账号此前签发的全部令牌立即失效。</p>
 *
 * @param newPassword 新密码明文，5—64 个字符
 */
public record AdminPasswordResetRequest(
        @NotBlank(message = "请输入新密码")
        @Size(min = AdminCredentialPolicy.PASSWORD_MIN_LENGTH, max = AdminCredentialPolicy.PASSWORD_MAX_LENGTH,
                message = "密码长度需为 " + AdminCredentialPolicy.PASSWORD_MIN_LENGTH + "—"
                        + AdminCredentialPolicy.PASSWORD_MAX_LENGTH + " 个字符")
        String newPassword) {
}
