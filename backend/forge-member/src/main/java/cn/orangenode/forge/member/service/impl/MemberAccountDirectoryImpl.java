package cn.orangenode.forge.member.service.impl;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;

import cn.orangenode.forge.framework.member.MemberAccountDirectory;
import cn.orangenode.forge.framework.member.MemberAccountView;
import cn.orangenode.forge.member.entity.MemberDeviceEntity;
import cn.orangenode.forge.member.entity.MemberUserEntity;
import cn.orangenode.forge.member.mapper.MemberDeviceMapper;
import cn.orangenode.forge.member.mapper.MemberUserMapper;
import cn.orangenode.forge.member.support.MemberStatus;
import cn.orangenode.forge.member.support.MemberValues;

import lombok.RequiredArgsConstructor;

/**
 * 普通用户账号目录端口实现：每次读取数据库当前状态，停用、改密与 VIP 调整在下一次请求立即生效。
 */
@Component
@RequiredArgsConstructor
public class MemberAccountDirectoryImpl implements MemberAccountDirectory {
    private final MemberUserMapper users;
    private final MemberDeviceMapper devices;

    /**
     * 读取账号视图。
     */
    @Override
    public Optional<MemberAccountView> findById(Long memberId) {
        MemberUserEntity user = users.selectById(memberId);
        if (user == null) {
            return Optional.empty();
        }
        return Optional.of(new MemberAccountView(user.getId(), user.getUsername(), user.getNickname(),
                MemberStatus.ENABLED.name().equals(user.getStatus()), MemberValues.instant(user.getVipExpireAt()),
                user.getSessionVersion() == null ? 0L : user.getSessionVersion()));
    }

    /**
     * 记录在线设备的活跃时间与来源地址。
     */
    @Override
    public void recordActivity(Long deviceId, String clientIp) {
        devices.update(null, Wrappers.<MemberDeviceEntity>lambdaUpdate()
                .set(MemberDeviceEntity::getLastActiveAt, MemberValues.now())
                .set(clientIp != null, MemberDeviceEntity::getClientIp, clientIp)
                .eq(MemberDeviceEntity::getId, deviceId).eq(MemberDeviceEntity::getOnline, true));
    }
}
