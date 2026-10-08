package cn.orangenode.forge.member.service;

import java.util.List;

import cn.orangenode.forge.member.response.MemberOwnDeviceResponse;

/**
 * 当前普通用户管理自己的登录设备。
 */
public interface MemberOwnDeviceService {

    /**
     * 列出自己的登录设备，在线设备在前。
     *
     * @return 设备列表
     */
    List<MemberOwnDeviceResponse> list();

    /**
     * 让自己的另一台设备下线；当前设备请使用退出登录。
     *
     * @param deviceId 设备记录 ID
     */
    void offline(Long deviceId);
}
