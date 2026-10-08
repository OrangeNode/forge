package cn.orangenode.forge.member.service;

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
}
