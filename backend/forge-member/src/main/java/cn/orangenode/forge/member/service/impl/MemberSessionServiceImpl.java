package cn.orangenode.forge.member.service.impl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;

import cn.orangenode.forge.core.exception.BusinessException;
import cn.orangenode.forge.core.response.ErrorCode;
import cn.orangenode.forge.framework.member.AppFeatureResolver;
import cn.orangenode.forge.framework.member.MemberToken;
import cn.orangenode.forge.framework.member.MemberTokenService;
import cn.orangenode.forge.member.entity.MemberDeviceEntity;
import cn.orangenode.forge.member.entity.MemberUserEntity;
import cn.orangenode.forge.member.mapper.MemberDeviceMapper;
import cn.orangenode.forge.member.mapper.MemberUserMapper;
import cn.orangenode.forge.member.service.MemberSessionService;
import cn.orangenode.forge.member.support.MemberOfflineReason;
import cn.orangenode.forge.member.support.MemberValues;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 登录设备与令牌会话实现。
 *
 * <p>签发前锁定账号行，保证同一账号并发登录时在线设备数的判断与踢出是串行的；
 * 设备记录保存令牌摘要，踢出或强制下线时按摘要删除 Redis 会话，令牌在下一次请求即失效。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemberSessionServiceImpl implements MemberSessionService {
    private final MemberUserMapper users;
    private final MemberDeviceMapper devices;
    private final MemberTokenService tokens;
    private final AppFeatureResolver features;

    /**
     * 清理过期设备，按台数上限踢出最早登录的设备，再签发新令牌。
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public MemberToken open(Long memberId, String deviceCode, String deviceName, String clientIp) {
        MemberUserEntity user = users.selectOne(Wrappers.<MemberUserEntity>lambdaQuery()
                .eq(MemberUserEntity::getId, memberId).last("FOR UPDATE"));
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        LocalDateTime now = MemberValues.now();
        MemberDeviceEntity current = null;
        List<MemberDeviceEntity> others = new ArrayList<>();
        for (MemberDeviceEntity device : onlineDevices(memberId)) {
            if (device.getDeviceCode().equals(deviceCode)) {
                tokens.revokeDigest(device.getTokenDigest());
            } else if (!tokens.isActive(device.getTokenDigest())) {
                markOffline(device, MemberOfflineReason.EXPIRED, now);
            } else {
                others.add(device);
            }
        }
        int maxDevices = features.current().maxDevices();
        for (int index = 0; index <= others.size() - maxDevices; index++) {
            MemberDeviceEntity oldest = others.get(index);
            tokens.revokeDigest(oldest.getTokenDigest());
            markOffline(oldest, MemberOfflineReason.LIMIT, now);
            log.info("普通用户 [{}] 超过同时登录台数 {}，踢出最早登录的设备记录 [{}]", memberId, maxDevices, oldest.getId());
        }
        current = devices.selectOne(Wrappers.<MemberDeviceEntity>lambdaQuery()
                .eq(MemberDeviceEntity::getMemberId, memberId).eq(MemberDeviceEntity::getDeviceCode, deviceCode));
        MemberToken token = tokens.newToken();
        if (current == null) {
            current = new MemberDeviceEntity();
            current.setMemberId(memberId);
            current.setDeviceCode(deviceCode);
            current.setCreatedAt(now);
        }
        current.setDeviceName(deviceName);
        current.setClientIp(clientIp);
        current.setTokenDigest(token.digest());
        current.setOnline(true);
        current.setOfflineReason(null);
        current.setOfflineAt(null);
        current.setLoginAt(now);
        current.setLastActiveAt(now);
        current.setUpdatedAt(now);
        if (current.getId() == null) {
            devices.insert(current);
        } else {
            devices.update(null, Wrappers.<MemberDeviceEntity>lambdaUpdate()
                    .set(MemberDeviceEntity::getDeviceName, deviceName)
                    .set(MemberDeviceEntity::getClientIp, clientIp)
                    .set(MemberDeviceEntity::getTokenDigest, token.digest())
                    .set(MemberDeviceEntity::getOnline, true)
                    .set(MemberDeviceEntity::getOfflineReason, null)
                    .set(MemberDeviceEntity::getOfflineAt, null)
                    .set(MemberDeviceEntity::getLoginAt, now)
                    .set(MemberDeviceEntity::getLastActiveAt, now)
                    .set(MemberDeviceEntity::getUpdatedAt, now)
                    .eq(MemberDeviceEntity::getId, current.getId()));
        }
        tokens.activate(token, memberId, current.getId(), user.getSessionVersion());
        return token;
    }

    /**
     * 撤销当前令牌并标记设备离线。
     */
    @Override
    @Transactional
    public void logout(Long deviceId, String token) {
        tokens.revoke(token);
        MemberDeviceEntity device = deviceId == null ? null : devices.selectById(deviceId);
        if (device != null && Boolean.TRUE.equals(device.getOnline())) {
            markOffline(device, MemberOfflineReason.LOGOUT, MemberValues.now());
        }
    }

    /**
     * 递增会话版本使全部令牌失效，并清理在线设备的 Redis 会话。
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void revokeAll(Long memberId, MemberOfflineReason reason) {
        users.update(null, Wrappers.<MemberUserEntity>lambdaUpdate()
                .setSql("session_version = session_version + 1")
                .set(MemberUserEntity::getUpdatedAt, MemberValues.now())
                .eq(MemberUserEntity::getId, memberId));
        LocalDateTime now = MemberValues.now();
        for (MemberDeviceEntity device : onlineDevices(memberId)) {
            tokens.revokeDigest(device.getTokenDigest());
            markOffline(device, reason, now);
        }
        log.info("普通用户 [{}] 的全部令牌已撤销，原因 {}", memberId, reason);
    }

    /**
     * 后台强制单个设备下线。
     */
    @Override
    @Transactional
    public void forceOffline(Long deviceId) {
        offline(deviceId, MemberOfflineReason.ADMIN);
    }

    /**
     * 撤销设备令牌并按给定原因标记离线。
     */
    @Override
    @Transactional
    public void offline(Long deviceId, MemberOfflineReason reason) {
        MemberDeviceEntity device = devices.selectById(deviceId);
        if (device == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "登录设备不存在");
        }
        if (!Boolean.TRUE.equals(device.getOnline())) {
            throw new BusinessException(ErrorCode.CONFLICT, "设备已离线");
        }
        tokens.revokeDigest(device.getTokenDigest());
        markOffline(device, reason, MemberValues.now());
    }

    /**
     * 查询账号在线设备，按登录时间升序（最早登录在前）。
     */
    private List<MemberDeviceEntity> onlineDevices(Long memberId) {
        return devices.selectList(Wrappers.<MemberDeviceEntity>lambdaQuery()
                .eq(MemberDeviceEntity::getMemberId, memberId).eq(MemberDeviceEntity::getOnline, true)
                .orderByAsc(MemberDeviceEntity::getLoginAt).orderByAsc(MemberDeviceEntity::getId));
    }

    /**
     * 条件更新为离线，清空令牌摘要。
     */
    private void markOffline(MemberDeviceEntity device, MemberOfflineReason reason, LocalDateTime now) {
        devices.update(null, Wrappers.<MemberDeviceEntity>lambdaUpdate()
                .set(MemberDeviceEntity::getOnline, false)
                .set(MemberDeviceEntity::getTokenDigest, null)
                .set(MemberDeviceEntity::getOfflineReason, reason.name())
                .set(MemberDeviceEntity::getOfflineAt, now)
                .set(MemberDeviceEntity::getUpdatedAt, now)
                .eq(MemberDeviceEntity::getId, device.getId()).eq(MemberDeviceEntity::getOnline, true));
    }
}
