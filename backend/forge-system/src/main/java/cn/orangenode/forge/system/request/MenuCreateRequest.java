package cn.orangenode.forge.system.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 菜单创建入参。
 *
 * <p>菜单只描述可见性，{@code routeKey} 必须是前端本地路由白名单中的标识：
 * 目录节点提交空值，页面节点提交标识。后端不接收组件路径或组件代码。</p>
 *
 * @param parentId 父菜单 ID 字符串，{@code 0} 或空表示顶级菜单
 * @param name     菜单名称，不超过 64 个字符
 * @param routeKey 前端本地路由标识，允许为空表示目录节点
 * @param sortNo   同级展示顺序
 */
public record MenuCreateRequest(
        String parentId,

        @NotBlank(message = "请输入菜单名称")
        @Size(max = 64, message = "菜单名称不能超过 64 个字符")
        String name,

        @Size(max = 64, message = "路由标识不能超过 64 个字符")
        String routeKey,

        @NotNull(message = "请输入展示顺序")
        Integer sortNo) {
}
