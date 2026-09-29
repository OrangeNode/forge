package cn.orangenode.forge.system.request;

import cn.orangenode.forge.framework.validation.ForgeEnum;
import cn.orangenode.forge.system.enums.AdminAccountStatus;

import jakarta.validation.constraints.NotBlank;

/**
 * 管理员启停入参。
 *
 * <p>状态取值为 {@link AdminAccountStatus} 的稳定代码，不接受序号；
 * 校验在接口层完成，服务端仍按当前登录身份与条件更新结果判断是否允许变更。</p>
 *
 * @param status 目标状态代码，{@code enabled} 启用、{@code disabled} 停用
 */
public record AdminStatusRequest(
        @NotBlank(message = "请选择账号状态")
        @ForgeEnum(value = AdminAccountStatus.class, message = "账号状态只能是 enabled 或 disabled")
        String status) {
}
