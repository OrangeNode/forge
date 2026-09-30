package cn.orangenode.forge.system.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import cn.orangenode.forge.system.validation.PermissionCodeElements;

/**
 * 菜单创建入参。
 *
 * <p>菜单同时描述前端可见性与接口权限：{@code routeKey} 必须是前端本地路由白名单中的标识，
 * 目录节点提交空值、页面节点提交标识；{@code permCodes} 是该节点声明的接口权限标识，
 * 目录节点通常为空，页面节点填写该页面全部接口的权限。</p>
 *
 * <p>权限标识的格式逐项校验，格式与 {@code @PreAuthorize} 使用的字符串完全一致；
 * 某个代码格式非法时通过 {@code data.fieldErrors.permCodes} 指出。</p>
 *
 * @param parentId  父菜单 ID 字符串，{@code 0} 或空表示顶级菜单
 * @param name      菜单名称，不超过 64 个字符
 * @param menuType   菜单类型：directory 目录、page 页面
 * @param icon       图标标识，由前端内置图标白名单解析
 * @param routeKey   前端本地路由标识，允许为空表示目录节点
 * @param permCodes  兼容旧客户端的权限代码列表，新客户端使用 permissions
 * @param permissions 权限代码、中文名称与说明
 * @param sortNo    同级展示顺序
 */
public record MenuCreateRequest(
        String parentId,

        @NotBlank(message = "请输入菜单名称")
        @Size(max = 64, message = "菜单名称不能超过 64 个字符")
        String name,

        @Pattern(regexp = "^(directory|page)$", message = "菜单类型只能是 directory 或 page")
        String menuType,

        @Size(max = 32, message = "图标标识不能超过 32 个字符")
        @Pattern(regexp = "^[a-z][a-z0-9-]*$", message = "图标标识格式不正确")
        String icon,

        @Size(max = 64, message = "路由标识不能超过 64 个字符")
        String routeKey,

        @Size(max = 50, message = "单个菜单最多声明 50 个权限标识")
        @PermissionCodeElements
        List<String> permCodes,

        @Valid
        @Size(max = 50, message = "单个菜单最多声明 50 个权限")
        List<MenuPermissionRequest> permissions,

        @NotNull(message = "请输入展示顺序")
        Integer sortNo) {
}
