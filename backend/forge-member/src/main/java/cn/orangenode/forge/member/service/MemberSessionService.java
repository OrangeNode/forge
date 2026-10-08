package cn.orangenode.forge.member.service;

import cn.orangenode.forge.framework.member.MemberToken;
import cn.orangenode.forge.member.support.MemberOfflineReason;

/**
 * 普通用户登录设备与令牌会话用例。
 *
 * <p>同一账号同时在线的设备数受 App 功能开关限制，超出时踢出最早登录的设备；
 * 同一设备重复登录只替换该设备的令牌，不占用额外名额。</p>
 */
public interface MemberSessionService {

    /**
     * 为已通过凭据校验的账号在指定设备上签发令牌，必须在调用方事务内执行。
     *
     * @param memberId   普通用户 ID
     * @param deviceCode 设备编号
     * @param deviceName 设备名称
     * @param clientIp   来源地址
     * @return 新令牌
     */
    MemberToken open(Long memberId, String deviceCode, String deviceName, String clientIp);

    /**
     * 退出当前设备：撤销当前令牌并标记设备离线。
     *
     * @param deviceId 当前令牌对应的设备记录 ID
     * @param token    当前令牌
     */
    void logout(Long deviceId, String token);

    /**
     * 撤销账号全部令牌并标记全部设备离线，必须在调用方事务内执行。
     *
     * @param memberId 普通用户 ID
     * @param reason   离线原因
     */
    void revokeAll(Long memberId, MemberOfflineReason reason);

    /**
     * 强制单个设备下线，设备不存在返回 404，已离线返回 409。
     *
     * @param deviceId 设备记录 ID
     */
    void forceOffline(Long deviceId);
}
