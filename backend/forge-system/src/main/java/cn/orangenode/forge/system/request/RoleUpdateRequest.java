package cn.orangenode.forge.system.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 角色修改入参。
 *
 * <p>允许修改角色代码、名称、说明与展示顺序。内置超级管理员角色的代码不允许修改，
 * 由服务端按配置识别后拒绝，避免用改代码的方式绕过内置权限语义。</p>
 *
 * @param code        角色代码，不超过 64 个字符
 * @param name        角色名称，不超过 64 个字符
 * @param description 角色说明，允许为空
 * @param sortNo      展示顺序，允许为空表示使用 0
 */
public record RoleUpdateRequest(
        @NotBlank(message = "请输入角色代码")
        @Size(max = 64, message = "角色代码不能超过 64 个字符")
        String code,

        @NotBlank(message = "请输入角色名称")
        @Size(max = 64, message = "角色名称不能超过 64 个字符")
        String name,

        @Size(max = 255, message = "角色说明不能超过 255 个字符")
        String description,

        Integer sortNo) {
}
