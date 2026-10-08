package cn.orangenode.forge.member.service.impl;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;

import cn.orangenode.forge.core.exception.BusinessException;
import cn.orangenode.forge.core.response.ErrorCode;
import cn.orangenode.forge.framework.member.AppFeatureResolver;
import cn.orangenode.forge.framework.member.CurrentMember;
import cn.orangenode.forge.member.converter.MemberConverter;
import cn.orangenode.forge.member.entity.MemberUserEntity;
import cn.orangenode.forge.member.mapper.MemberUserMapper;
import cn.orangenode.forge.member.request.MemberPasswordChangeRequest;
import cn.orangenode.forge.member.request.MemberProfileUpdateRequest;
import cn.orangenode.forge.member.response.MemberProfileResponse;
import cn.orangenode.forge.member.service.MemberProfileService;
import cn.orangenode.forge.member.service.MemberSessionService;
import cn.orangenode.forge.member.support.MemberOfflineReason;
import cn.orangenode.forge.member.support.MemberValues;

import lombok.RequiredArgsConstructor;

/**
 * 当前普通用户资料实现。
 */
@Service
@RequiredArgsConstructor
public class MemberProfileServiceImpl implements MemberProfileService {
    private final MemberUserMapper users;
    private final MemberSessionService sessions;
    private final PasswordEncoder passwordEncoder;
    private final AppFeatureResolver features;
    private final MemberConverter converter;

    /**
     * 读取当前用户资料。
     */
    @Override
    public MemberProfileResponse current() {
        return converter.profile(currentUser(), features.current().vipRequired());
    }

    /**
     * 修改昵称与头像。
     */
    @Override
    @Transactional
    public MemberProfileResponse update(MemberProfileUpdateRequest request) {
        MemberUserEntity user = currentUser();
        users.update(null, Wrappers.<MemberUserEntity>lambdaUpdate()
                .set(MemberUserEntity::getNickname, request.nickname().strip())
                .set(MemberUserEntity::getAvatarUrl, MemberValues.clean(request.avatarUrl()))
                .set(MemberUserEntity::getUpdatedAt, MemberValues.now())
                .eq(MemberUserEntity::getId, user.getId()));
        return current();
    }

    /**
     * 校验原密码后修改密码并撤销全部令牌。
     */
    @Override
    @Transactional
    public void changePassword(MemberPasswordChangeRequest request) {
        MemberUserEntity user = currentUser();
        if (!passwordEncoder.matches(request.oldPassword(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "原密码不正确");
        }
        users.update(null, Wrappers.<MemberUserEntity>lambdaUpdate()
                .set(MemberUserEntity::getPasswordHash, passwordEncoder.encode(request.newPassword()))
                .set(MemberUserEntity::getUpdatedAt, MemberValues.now())
                .eq(MemberUserEntity::getId, user.getId()));
        sessions.revokeAll(user.getId(), MemberOfflineReason.REVOKED);
    }

    /**
     * 读取当前登录用户记录。
     */
    private MemberUserEntity currentUser() {
        MemberUserEntity user = users.selectById(CurrentMember.require().id());
        if (user == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "请先登录");
        }
        return user;
    }
}
