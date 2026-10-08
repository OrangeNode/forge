package cn.orangenode.forge.member.converter;

import java.time.Instant;

import org.springframework.stereotype.Component;

import cn.orangenode.forge.member.entity.MemberDeviceEntity;
import cn.orangenode.forge.member.entity.MemberUserEntity;
import cn.orangenode.forge.member.entity.MemberVipLogEntity;
import cn.orangenode.forge.member.response.MemberDeviceResponse;
import cn.orangenode.forge.member.response.MemberProfileResponse;
import cn.orangenode.forge.member.response.MemberUserResponse;
import cn.orangenode.forge.member.response.MemberVipLogResponse;
import cn.orangenode.forge.member.support.MemberValues;

/**
 * 普通用户实体与对外响应的显式转换，不输出密码摘要与令牌摘要。
 */
@Component
public class MemberConverter {

    /**
     * 转换为 App 当前用户资料。
     */
    public MemberProfileResponse profile(MemberUserEntity user, boolean vipRequired) {
        return new MemberProfileResponse(MemberValues.textId(user.getId()), user.getEmail(), user.getNickname(),
                user.getAvatarUrl(), vipActive(user), MemberValues.time(user.getVipExpireAt()), vipRequired);
    }

    /**
     * 转换为后台用户项。
     */
    public MemberUserResponse user(MemberUserEntity user, long onlineDevices) {
        return new MemberUserResponse(MemberValues.textId(user.getId()), user.getEmail(), user.getNickname(),
                user.getAvatarUrl(), user.getStatus(), vipActive(user), MemberValues.time(user.getVipExpireAt()),
                onlineDevices, user.getRegisterIp(), MemberValues.time(user.getLastLoginAt()), user.getLastLoginIp(),
                MemberValues.time(user.getCreatedAt()));
    }

    /**
     * 转换为登录设备记录，不输出令牌摘要。
     */
    public MemberDeviceResponse device(MemberDeviceEntity device, String memberEmail) {
        return new MemberDeviceResponse(MemberValues.textId(device.getId()), MemberValues.textId(device.getMemberId()),
                memberEmail, device.getDeviceCode(), device.getDeviceName(), device.getClientIp(),
                Boolean.TRUE.equals(device.getOnline()), device.getOfflineReason(), MemberValues.time(device.getLoginAt()),
                MemberValues.time(device.getLastActiveAt()), MemberValues.time(device.getOfflineAt()));
    }

    /**
     * 转换为 VIP 变更记录。
     */
    public MemberVipLogResponse vipLog(MemberVipLogEntity log) {
        return new MemberVipLogResponse(MemberValues.textId(log.getId()), log.getChangeType(), log.getDays(),
                MemberValues.time(log.getBeforeExpireAt()), MemberValues.time(log.getAfterExpireAt()), log.getRemark(),
                MemberValues.textId(log.getOperatorId()), MemberValues.time(log.getCreatedAt()));
    }

    /**
     * 判断 VIP 当前是否有效。
     */
    private boolean vipActive(MemberUserEntity user) {
        Instant expireAt = MemberValues.instant(user.getVipExpireAt());
        return expireAt != null && expireAt.isAfter(Instant.now());
    }
}
