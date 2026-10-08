package cn.orangenode.forge.member.service;

import java.util.List;

import cn.orangenode.forge.core.page.PageResponse;
import cn.orangenode.forge.member.request.MemberAdminPasswordRequest;
import cn.orangenode.forge.member.request.MemberStatusRequest;
import cn.orangenode.forge.member.request.MemberVipExtendRequest;
import cn.orangenode.forge.member.request.MemberVipSetRequest;
import cn.orangenode.forge.member.response.MemberDeviceResponse;
import cn.orangenode.forge.member.response.MemberUserResponse;
import cn.orangenode.forge.member.response.MemberVipLogResponse;

/**
 * 后台普通用户管理用例：查询、启停、重置密码、VIP 与登录设备。
 */
public interface MemberManagementService {

    /**
     * 分页查询普通用户；vip 取值 ACTIVE（有效）、EXPIRED（已过期）、NONE（从未开通）。
     */
    PageResponse<MemberUserResponse> page(Integer pageNum, Integer pageSize, String username, String status, String vip);

    /**
     * 查询普通用户详情。
     */
    MemberUserResponse detail(Long id);

    /**
     * 启停普通用户，状态未变化返回 409；停用后全部令牌立即失效。
     */
    MemberUserResponse changeStatus(Long id, MemberStatusRequest request);

    /**
     * 重置普通用户密码，全部令牌立即失效。
     */
    void resetPassword(Long id, MemberAdminPasswordRequest request);

    /**
     * 开通或延长 VIP。
     */
    MemberUserResponse extendVip(Long id, MemberVipExtendRequest request);

    /**
     * 直接设置或取消 VIP 到期时间。
     */
    MemberUserResponse setVip(Long id, MemberVipSetRequest request);

    /**
     * 查询 VIP 变更记录，按时间倒序。
     */
    List<MemberVipLogResponse> vipLogs(Long id);

    /**
     * 分页查询登录设备，可按用户、邮箱与在线状态筛选。
     */
    PageResponse<MemberDeviceResponse> devices(Integer pageNum, Integer pageSize, Long memberId, String username,
            Boolean online);

    /**
     * 强制单个设备下线。
     */
    void forceOffline(Long deviceId);

    /**
     * 强制普通用户全部设备下线。
     */
    void forceOfflineAll(Long memberId);
}
