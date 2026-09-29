package cn.orangenode.forge.system.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 权限修改入参。
 *
 * <p>只允许修改名称与说明：权限代码是 {@code @PreAuthorize} 与角色授权关系的引用键，
 * 改动它会让既有授权静默指向另一个语义，因此不开放修改。</p>
 *
 * @param name        权限名称，不超过 64 个字符
 * @param description 权限说明，允许为空
 */
public record PermissionUpdateRequest(
        @NotBlank(message = "请输入权限名称")
        @Size(max = 64, message = "权限名称不能超过 64 个字符")
        String name,

        @Size(max = 255, message = "权限说明不能超过 255 个字符")
        String description) {
}
