package cn.orangenode.forge.member.service;

import cn.orangenode.forge.member.request.MemberLoginRequest;
import cn.orangenode.forge.member.request.MemberRegisterRequest;
import cn.orangenode.forge.member.response.MemberLoginResponse;

/**
 * 普通用户注册、登录与退出用例；忘记密码由管理员在后台重置。
 */
public interface MemberAuthService {

    /**
     * 用户名注册并在当前设备登录；要求开放注册，同一来源地址每日注册数受限。
     *
     * @param request  注册信息
     * @param clientIp 来源地址
     * @return 登录结果
     */
    MemberLoginResponse register(MemberRegisterRequest request, String clientIp);

    /**
     * 用户名密码登录，账号不存在与密码错误使用同一提示，失败次数受限。
     *
     * @param request  登录信息
     * @param clientIp 来源地址
     * @return 登录结果
     */
    MemberLoginResponse login(MemberLoginRequest request, String clientIp);

    /**
     * 退出当前设备。
     */
    void logout();
}
