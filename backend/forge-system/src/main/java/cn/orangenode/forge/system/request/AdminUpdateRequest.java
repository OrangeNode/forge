package cn.orangenode.forge.system.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 管理员基础信息修改入参。
 *
 * <p>只允许修改显示名称：用户名是登录凭据的一部分，停用状态、密码与角色分别由各自的接口处理，
 * 避免一个接口同时承担多种互相影响的语义。</p>
 *
 * @param displayName 显示名称，不超过 64 个字符
 */
public record AdminUpdateRequest(
        @NotBlank(message = "请输入显示名称")
        @Size(max = 64, message = "显示名称不能超过 64 个字符")
        String displayName) {
}
