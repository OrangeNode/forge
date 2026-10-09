package cn.orangenode.forge.member.converter;

import java.time.Instant;

import org.springframework.stereotype.Component;

import cn.orangenode.forge.framework.catalog.VideoCard;
import cn.orangenode.forge.member.entity.MemberActivationCodeEntity;
import cn.orangenode.forge.member.entity.MemberDeviceEntity;
import cn.orangenode.forge.member.entity.MemberFavoriteEntity;
import cn.orangenode.forge.member.entity.MemberWatchHistoryEntity;
import cn.orangenode.forge.member.entity.MemberUserEntity;
import cn.orangenode.forge.member.entity.MemberVipLogEntity;
import cn.orangenode.forge.member.response.MemberActivationCodeResponse;
import cn.orangenode.forge.member.response.MemberDeviceResponse;
import cn.orangenode.forge.member.response.MemberFavoriteResponse;
import cn.orangenode.forge.member.response.MemberHistoryResponse;
import cn.orangenode.forge.member.response.MemberOwnDeviceResponse;
import cn.orangenode.forge.member.response.MemberProfileResponse;
import cn.orangenode.forge.member.response.MemberUserResponse;
import cn.orangenode.forge.member.response.MemberVipLogResponse;
import cn.orangenode.forge.member.support.MemberActivationStatus;
import cn.orangenode.forge.member.support.MemberValues;

/**
 * 普通用户实体与对外响应的显式转换，不输出密码摘要与令牌摘要。
 */
@Component
public class MemberConverter {

    /**
     * 转换观看历史，影片展示信息取目录中的最新值。
     */
    public MemberHistoryResponse history(MemberWatchHistoryEntity history, VideoCard card) {
        return new MemberHistoryResponse(MemberValues.textId(history.getVideoId()), card.name(), card.posterUrl(),
                card.remarks(), MemberValues.textId(history.getLineId()), history.getEpisodeIndex(),
                history.getEpisodeName(), history.getPositionSeconds(), history.getDurationSeconds(),
                MemberValues.time(history.getUpdatedAt()));
    }

    /**
     * 转换收藏条目。
     */
    public MemberFavoriteResponse favorite(MemberFavoriteEntity favorite, VideoCard card) {
        return new MemberFavoriteResponse(MemberValues.textId(favorite.getVideoId()), card.name(), card.posterUrl(),
                card.remarks(), card.year(), MemberValues.time(favorite.getCreatedAt()));
    }

    /**
     * 转换当前用户自己的设备，不输出设备编号与令牌摘要。
     */
    public MemberOwnDeviceResponse ownDevice(MemberDeviceEntity device, Long currentDeviceId) {
        return new MemberOwnDeviceResponse(MemberValues.textId(device.getId()), device.getDeviceName(),
                device.getClientIp(), Boolean.TRUE.equals(device.getOnline()), device.getId().equals(currentDeviceId),
                MemberValues.time(device.getLoginAt()), MemberValues.time(device.getLastActiveAt()));
    }

    /**
     * 转换为 App 当前用户资料。
     */
    public MemberProfileResponse profile(MemberUserEntity user, boolean vipRequired) {
        return new MemberProfileResponse(MemberValues.textId(user.getId()), user.getUsername(), user.getNickname(),
                user.getAvatarUrl(), vipActive(user), MemberValues.time(user.getVipExpireAt()), vipRequired);
    }

    /**
     * 转换为后台用户项。
     */
    public MemberUserResponse user(MemberUserEntity user, long onlineDevices) {
        return new MemberUserResponse(MemberValues.textId(user.getId()), user.getUsername(), user.getNickname(),
                user.getAvatarUrl(), user.getStatus(), vipActive(user), MemberValues.time(user.getVipExpireAt()),
                onlineDevices, user.getRegisterIp(), MemberValues.time(user.getLastLoginAt()), user.getLastLoginIp(),
                MemberValues.time(user.getCreatedAt()));
    }

    /**
     * 转换为登录设备记录，不输出令牌摘要。
     */
    public MemberDeviceResponse device(MemberDeviceEntity device, String memberUsername) {
        return new MemberDeviceResponse(MemberValues.textId(device.getId()), MemberValues.textId(device.getMemberId()),
                memberUsername, device.getDeviceCode(), device.getDeviceName(), device.getClientIp(),
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
     * 转换后台激活码列表项：激活码每 4 位加短横线，旧数据未使用且已过截止时间显示为 EXPIRED。
     */
    public MemberActivationCodeResponse activation(MemberActivationCodeEntity entity) {
        String status = entity.getStatus();
        Instant expireAt = MemberValues.instant(entity.getExpireAt());
        if (MemberActivationStatus.UNUSED.name().equals(status) && expireAt != null && !expireAt.isAfter(Instant.now())) {
            status = MemberActivationStatus.EXPIRED.name();
        }
        return new MemberActivationCodeResponse(MemberValues.textId(entity.getId()), formatCode(entity.getCode()), status,
                entity.getVipDays(), MemberValues.time(entity.getExpireAt()), entity.getRemark(),
                MemberValues.textId(entity.getUsedMemberId()), entity.getUsedUsername(), MemberValues.time(entity.getUsedAt()),
                entity.getUsedIp(), MemberValues.time(entity.getRevokedAt()), MemberValues.time(entity.getCreatedAt()));
    }

    /**
     * 激活码每 4 位插入短横线，便于抄写。
     */
    private String formatCode(String code) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < code.length(); i++) {
            if (i > 0 && i % 4 == 0) {
                text.append('-');
            }
            text.append(code.charAt(i));
        }
        return text.toString();
    }

    /**
     * 判断 VIP 当前是否有效。
     */
    private boolean vipActive(MemberUserEntity user) {
        Instant expireAt = MemberValues.instant(user.getVipExpireAt());
        return expireAt != null && expireAt.isAfter(Instant.now());
    }
}
