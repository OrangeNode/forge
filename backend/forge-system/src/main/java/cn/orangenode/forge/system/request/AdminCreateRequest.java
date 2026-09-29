package cn.orangenode.forge.system.request;

import java.util.List;

import cn.orangenode.forge.system.credential.AdminCredentialPolicy;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 管理员创建入参。
 *
 * <p>账号状态、创建者与时间由服务端决定，不接受客户端提交；
 * 用户名在保存前按 {@link AdminCredentialPolicy} 规范化并校验长度，
 * 密码按创建规则校验长度后只保存编码结果。</p>
 *
 * @param username    登录用户名，4—32 个字符，保存前去空格并转小写
 * @param password    登录密码明文，8—64 个字符，不写入日志与响应
 * @param displayName 显示名称，不超过 64 个字符
 * @param roleIds     初始角色 ID 集合，允许为空表示暂不分配角色
 */
public record AdminCreateRequest(
        @NotBlank(message = "请输入用户名")
        @Size(min = AdminCredentialPolicy.USERNAME_MIN_LENGTH, max = AdminCredentialPolicy.USERNAME_MAX_LENGTH,
                message = "用户名长度需为 4—32 个字符")
        String username,

        @NotBlank(message = "请输入密码")
        @Size(min = AdminCredentialPolicy.PASSWORD_MIN_LENGTH, max = AdminCredentialPolicy.PASSWORD_MAX_LENGTH,
                message = "密码长度需为 8—64 个字符")
        String password,

        @NotBlank(message = "请输入显示名称")
        @Size(max = 64, message = "显示名称不能超过 64 个字符")
        String displayName,

        @Size(max = 50, message = "一次最多分配 50 个角色")
        List<String> roleIds) {
}
