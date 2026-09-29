package cn.orangenode.forge.system.service;

import java.util.List;

import cn.orangenode.forge.system.request.AdminLoginRequest;
import cn.orangenode.forge.system.response.AdminLoginResponse;
import cn.orangenode.forge.system.response.AdminMenuResponse;
import cn.orangenode.forge.system.response.AdminProfileResponse;

/**
 * 管理员认证用例。
 *
 * <p>面向登录、退出与当前身份查询：凭据校验、失败限流、令牌签发与撤销都在本用例内完成，
 * 接口层不直接处理密码与令牌。</p>
 */
public interface AdminAuthService {

    /**
     * 校验凭据并签发令牌。
     *
     * @param request 登录入参
     * @return 令牌与当前管理员基础信息
     */
    AdminLoginResponse login(AdminLoginRequest request);

    /**
     * 退出登录并撤销当前令牌。
     *
     * @param token 当前请求使用的令牌，缺失时不执行撤销
     */
    void logout(String token);

    /**
     * 查询当前管理员身份与权限。
     *
     * @param adminId 当前管理员 ID，来自安全上下文
     * @return 身份与权限响应
     */
    AdminProfileResponse currentProfile(Long adminId);

    /**
     * 查询当前管理员可见菜单。
     *
     * @param adminId 当前管理员 ID，来自安全上下文
     * @return 菜单树
     */
    List<AdminMenuResponse> currentMenus(Long adminId);
}
