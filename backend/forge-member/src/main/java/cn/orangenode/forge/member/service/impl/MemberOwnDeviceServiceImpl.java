package cn.orangenode.forge.member.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;

import cn.orangenode.forge.core.exception.BusinessException;
import cn.orangenode.forge.core.response.ErrorCode;
import cn.orangenode.forge.framework.member.AuthenticatedMember;
import cn.orangenode.forge.framework.member.CurrentMember;
import cn.orangenode.forge.member.converter.MemberConverter;
import cn.orangenode.forge.member.entity.MemberDeviceEntity;
import cn.orangenode.forge.member.mapper.MemberDeviceMapper;
import cn.orangenode.forge.member.response.MemberOwnDeviceResponse;
import cn.orangenode.forge.member.service.MemberOwnDeviceService;
import cn.orangenode.forge.member.service.MemberSessionService;
import cn.orangenode.forge.member.support.MemberOfflineReason;

import lombok.RequiredArgsConstructor;

/**
 * 当前普通用户自己的登录设备实现：只能查看和操作属于自己的设备记录。
 */
@Service
@RequiredArgsConstructor
public class MemberOwnDeviceServiceImpl implements MemberOwnDeviceService {
    private static final int MAX_DEVICES_SHOWN = 20;
    private final MemberDeviceMapper devices;
    private final MemberSessionService sessions;
    private final MemberConverter converter;

    /**
     * 列出自己的设备：在线在前，再按最近活跃时间倒序，最多返回 20 台。
     */
    @Override
    public List<MemberOwnDeviceResponse> list() {
        AuthenticatedMember member = CurrentMember.require();
        return devices.selectList(Wrappers.<MemberDeviceEntity>lambdaQuery()
                .eq(MemberDeviceEntity::getMemberId, member.id())
                .orderByDesc(MemberDeviceEntity::getOnline)
                .orderByDesc(MemberDeviceEntity::getLastActiveAt)
                .orderByDesc(MemberDeviceEntity::getId)
                .last("LIMIT " + MAX_DEVICES_SHOWN))
                .stream().map(device -> converter.ownDevice(device, member.deviceId())).toList();
    }

    /**
     * 让自己的另一台设备下线；不属于自己的设备按不存在处理。
     */
    @Override
    @Transactional
    public void offline(Long deviceId) {
        AuthenticatedMember member = CurrentMember.require();
        MemberDeviceEntity device = devices.selectById(deviceId);
        if (device == null || !device.getMemberId().equals(member.id())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "登录设备不存在");
        }
        if (device.getId().equals(member.deviceId())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "当前设备请使用退出登录");
        }
        sessions.offline(deviceId, MemberOfflineReason.LOGOUT);
    }
}
