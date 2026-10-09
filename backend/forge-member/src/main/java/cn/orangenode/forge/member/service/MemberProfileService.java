package cn.orangenode.forge.member.service;

import cn.orangenode.forge.member.request.MemberActivationRedeemRequest;
import cn.orangenode.forge.member.request.MemberPasswordChangeRequest;
import cn.orangenode.forge.member.request.MemberProfileUpdateRequest;
import cn.orangenode.forge.member.response.MemberProfileResponse;

/**
 * 当前普通用户的个人资料用例，用户身份只来自安全上下文。
 */
public interface MemberProfileService {

    /**
     * 读取当前用户资料与 VIP 状态。
     */
    MemberProfileResponse current();

    /**
     * 修改昵称与头像。
     */
    MemberProfileResponse update(MemberProfileUpdateRequest request);

    /**
     * 校验原密码后修改密码，全部令牌失效。
     */
    void changePassword(MemberPasswordChangeRequest request);

    /**
     * 用激活码续费 VIP：时长接在当前到期时间之后，已过期或未开通从现在起算；激活码与注册共用，一码只能使用一次。
     *
     * @param request  激活码
     * @param clientIp 来源地址，输错次数按来源地址每日计数
     * @return 续费后的资料与 VIP 状态
     */
    MemberProfileResponse redeem(MemberActivationRedeemRequest request, String clientIp);
}
