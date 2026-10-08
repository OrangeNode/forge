package cn.orangenode.forge.member.service;

import cn.orangenode.forge.member.request.MemberCodeRequest;
import cn.orangenode.forge.member.request.MemberLoginRequest;
import cn.orangenode.forge.member.request.MemberPasswordResetRequest;
import cn.orangenode.forge.member.request.MemberRegisterRequest;
import cn.orangenode.forge.member.response.MemberLoginResponse;

/**
 * 普通用户注册、登录、找回密码与退出用例。
 */
public interface MemberAuthService {

    /**
     * 发送邮箱验证码；注册要求开放注册且邮箱未注册，找回密码对未注册邮箱静默不发信。
     */
    void sendCode(MemberCodeRequest request, String clientIp);

    /**
     * 邮箱验证码注册并在当前设备登录。
     */
    MemberLoginResponse register(MemberRegisterRequest request, String clientIp);

    /**
     * 邮箱密码登录，账号不存在与密码错误使用同一提示，失败次数受限。
     */
    MemberLoginResponse login(MemberLoginRequest request, String clientIp);

    /**
     * 邮箱验证码重置密码，成功后全部令牌失效。
     */
    void resetPassword(MemberPasswordResetRequest request);

    /**
     * 退出当前设备。
     */
    void logout();
}
