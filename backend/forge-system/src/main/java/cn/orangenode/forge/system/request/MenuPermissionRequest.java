package cn.orangenode.forge.system.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 菜单节点声明的接口权限入参。
 *
 * <p>权限代码用于后端鉴权，中文名称与说明用于菜单和角色授权页面展示；三者作为菜单字段一起保存，
 * 不再依赖独立权限主表。</p>
 *
 * @param code        权限代码，格式为模块:资源:动作
 * @param name        中文名称，例如“查询存储配置”
 * @param description 权限用途说明，允许为空
 */
public record MenuPermissionRequest(
        @NotBlank(message = "请输入权限标识")
        @Pattern(regexp = "^[a-z0-9_-]{1,32}:[a-z0-9_-]{1,32}:[a-z0-9_-]{1,32}$",
                message = "权限标识格式为 模块:资源:动作")
        String code,

        @NotBlank(message = "请输入权限中文名称")
        @Size(max = 64, message = "权限中文名称不能超过 64 个字符")
        String name,

        @Size(max = 255, message = "权限说明不能超过 255 个字符")
        String description) {
}
