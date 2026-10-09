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
import cn.orangenode.forge.member.request.MemberActivationRedeemRequest;
import cn.orangenode.forge.member.request.MemberPasswordChangeRequest;
import cn.orangenode.forge.member.request.MemberProfileUpdateRequest;
import cn.orangenode.forge.member.request.MemberVipExtendRequest;
import cn.orangenode.forge.member.response.MemberProfileResponse;
import cn.orangenode.forge.member.service.MemberActivationService;
import cn.orangenode.forge.member.service.MemberManagementService;
import cn.orangenode.forge.member.service.MemberProfileService;
import cn.orangenode.forge.member.service.MemberSessionService;
import cn.orangenode.forge.member.support.MemberActivationUsage;
import cn.orangenode.forge.member.support.MemberOfflineReason;
import cn.orangenode.forge.member.support.MemberValues;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 当前普通用户资料实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemberProfileServiceImpl implements MemberProfileService {
    private final MemberUserMapper users;
    private final MemberSessionService sessions;
    private final PasswordEncoder passwordEncoder;
    private final AppFeatureResolver features;
    private final MemberConverter converter;
    private final MemberActivationService activations;
    private final MemberManagementService members;

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
     * 校验并核销激活码后按其会员天数顺延 VIP；不含会员时长的旧码返回 400 且不核销（事务回滚）。
     */
    @Override
    @Transactional
    public MemberProfileResponse redeem(MemberActivationRedeemRequest request, String clientIp) {
        MemberUserEntity user = currentUser();
        Long codeId = activations.check(request.activationCode(), clientIp);
        Integer vipDays = activations.consume(codeId, user.getId(), user.getUsername(), clientIp,
                MemberActivationUsage.RENEW);
        if (vipDays == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "该激活码不含会员时长，不能用于续费");
        }
        members.extendVip(user.getId(), new MemberVipExtendRequest(vipDays, "激活码续费 " + vipDays + " 天"));
        log.info("激活码续费成功，用户 ID={}，激活码 ID={}，会员 {} 天", user.getId(), codeId, vipDays);
        return current();
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
