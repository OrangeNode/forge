package cn.orangenode.forge.system.request;

import cn.orangenode.forge.system.support.PermissionCodeFormat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 权限创建入参。
 *
 * <p>权限代码格式固定为 {@code 模块:资源:动作}，格式规则来自 {@link PermissionCodeFormat}，
 * 与数据库种子和保存前的规范化共用同一份定义；唯一性由数据库唯一约束兜底，冲突映射为 409。</p>
 *
 * @param code        权限代码，格式为 模块:资源:动作
 * @param name        权限名称，不超过 64 个字符
 * @param description 权限说明，允许为空
 */
public record PermissionCreateRequest(
        @NotBlank(message = "请输入权限代码")
        @Pattern(regexp = PermissionCodeFormat.PATTERN, message = PermissionCodeFormat.DESCRIPTION)
        String code,

        @NotBlank(message = "请输入权限名称")
        @Size(max = 64, message = "权限名称不能超过 64 个字符")
        String name,

        @Size(max = 255, message = "权限说明不能超过 255 个字符")
        String description) {
}
