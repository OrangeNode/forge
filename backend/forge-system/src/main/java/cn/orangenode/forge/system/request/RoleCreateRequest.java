package cn.orangenode.forge.system.request;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 角色创建入参。
 *
 * <p>角色代码保存前统一去空格并规范为小写：数据库唯一键不区分大小写，
 * 应用层使用同一条规则，避免出现 {@code Ops} 与 {@code ops} 两个角色。</p>
 *
 * @param code        角色代码，不超过 64 个字符
 * @param name        角色名称，不超过 64 个字符
 * @param description 角色说明，允许为空
 * @param sortNo      展示顺序，允许为空表示使用 0
 * @param permissionIds 初始权限 ID 集合，允许为空
 * @param menuIds       初始菜单 ID 集合，允许为空
 */
public record RoleCreateRequest(
        @NotBlank(message = "请输入角色代码")
        @Size(max = 64, message = "角色代码不能超过 64 个字符")
        String code,

        @NotBlank(message = "请输入角色名称")
        @Size(max = 64, message = "角色名称不能超过 64 个字符")
        String name,

        @Size(max = 255, message = "角色说明不能超过 255 个字符")
        String description,

        Integer sortNo,

        @Size(max = 500, message = "一次最多授予 500 个权限")
        List<String> permissionIds,

        @Size(max = 500, message = "一次最多授予 500 个菜单")
        List<String> menuIds) {
}
