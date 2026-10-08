package cn.orangenode.forge.member.service.impl;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;

import cn.orangenode.forge.core.exception.BusinessException;
import cn.orangenode.forge.core.response.ErrorCode;
import cn.orangenode.forge.framework.member.AppFeatureResolver;
import cn.orangenode.forge.framework.member.AuthenticatedMember;
import cn.orangenode.forge.framework.member.CurrentMember;
import cn.orangenode.forge.framework.member.MemberToken;
import cn.orangenode.forge.framework.security.LoginAttemptGuard;
import cn.orangenode.forge.member.converter.MemberConverter;
import cn.orangenode.forge.member.entity.MemberUserEntity;
import cn.orangenode.forge.member.mail.MemberCodePurpose;
import cn.orangenode.forge.member.mapper.MemberUserMapper;
import cn.orangenode.forge.member.request.MemberCodeRequest;
import cn.orangenode.forge.member.request.MemberLoginRequest;
import cn.orangenode.forge.member.request.MemberPasswordResetRequest;
import cn.orangenode.forge.member.request.MemberRegisterRequest;
import cn.orangenode.forge.member.response.MemberLoginResponse;
import cn.orangenode.forge.member.service.MemberAuthService;
import cn.orangenode.forge.member.service.MemberCodeService;
import cn.orangenode.forge.member.service.MemberSessionService;
import cn.orangenode.forge.member.support.MemberOfflineReason;
import cn.orangenode.forge.member.support.MemberStatus;
import cn.orangenode.forge.member.support.MemberValues;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 普通用户认证实现。
 *
 * <p>登录失败计数复用基础层限流组件，键名加普通用户前缀，与管理员用户名互不影响；
 * 账号不存在与密码错误返回同一句 401 提示。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemberAuthServiceImpl implements MemberAuthService {
    private static final String ATTEMPT_PREFIX = "member:";
    private static final String TOKEN_TYPE = "Bearer";
    private static final String BAD_CREDENTIALS = "邮箱或密码错误";
    private final MemberUserMapper users;
    private final MemberCodeService codes;
    private final MemberSessionService sessions;
    private final PasswordEncoder passwordEncoder;
    private final LoginAttemptGuard attemptGuard;
    private final AppFeatureResolver features;
    private final MemberConverter converter;

    /**
     * 按用途检查注册开关与邮箱状态后发送验证码。
     */
    @Override
    public void sendCode(MemberCodeRequest request, String clientIp) {
        String email = MemberValues.email(request.email());
        MemberCodePurpose purpose = MemberCodePurpose.valueOf(request.purpose());
        boolean exists = findByEmail(email) != null;
        if (purpose == MemberCodePurpose.REGISTER) {
            assertRegistrationOpen();
            if (exists) {
                throw new BusinessException(ErrorCode.CONFLICT, "该邮箱已注册，请直接登录");
            }
        } else if (!exists) {
            log.info("找回密码的邮箱未注册，静默不发信，收件人={}", MemberValues.mask(email));
            return;
        }
        codes.send(email, purpose, clientIp);
    }

    /**
     * 校验验证码后创建账号并登录当前设备；并发重复注册由唯一键兜底返回 409。
     */
    @Override
    @Transactional
    public MemberLoginResponse register(MemberRegisterRequest request, String clientIp) {
        assertRegistrationOpen();
        String email = MemberValues.email(request.email());
        if (findByEmail(email) != null) {
            throw new BusinessException(ErrorCode.CONFLICT, "该邮箱已注册，请直接登录");
        }
        codes.consume(email, MemberCodePurpose.REGISTER, request.code());
        MemberUserEntity user = new MemberUserEntity();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        String nickname = MemberValues.clean(request.nickname());
        user.setNickname(nickname == null ? defaultNickname(email) : nickname);
        user.setStatus(MemberStatus.ENABLED.name());
        user.setSessionVersion(0L);
        user.setRegisterIp(clientIp);
        user.setLastLoginAt(MemberValues.now());
        user.setLastLoginIp(clientIp);
        user.setCreatedAt(MemberValues.now());
        user.setUpdatedAt(MemberValues.now());
        try {
            users.insert(user);
        } catch (DuplicateKeyException duplicate) {
            throw new BusinessException(ErrorCode.CONFLICT, "该邮箱已注册，请直接登录");
        }
        log.info("普通用户注册成功，用户 ID={}，邮箱={}", user.getId(), MemberValues.mask(email));
        MemberToken token = sessions.open(user.getId(), request.deviceCode(), request.deviceName().strip(), clientIp);
        return response(token, users.selectById(user.getId()));
    }

    /**
     * 校验凭据与账号状态后在当前设备签发令牌。
     */
    @Override
    @Transactional
    public MemberLoginResponse login(MemberLoginRequest request, String clientIp) {
        String email = MemberValues.email(request.email());
        attemptGuard.assertAllowed(ATTEMPT_PREFIX + email);
        MemberUserEntity user = findByEmail(email);
        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            attemptGuard.recordFailure(ATTEMPT_PREFIX + email);
            throw new BusinessException(ErrorCode.UNAUTHORIZED, BAD_CREDENTIALS);
        }
        if (!MemberStatus.ENABLED.name().equals(user.getStatus())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "账号已停用，请联系客服");
        }
        attemptGuard.clear(ATTEMPT_PREFIX + email);
        users.update(null, Wrappers.<MemberUserEntity>lambdaUpdate()
                .set(MemberUserEntity::getLastLoginAt, MemberValues.now())
                .set(MemberUserEntity::getLastLoginIp, clientIp)
                .eq(MemberUserEntity::getId, user.getId()));
        MemberToken token = sessions.open(user.getId(), request.deviceCode(), request.deviceName().strip(), clientIp);
        return response(token, users.selectById(user.getId()));
    }

    /**
     * 校验验证码后重置密码并撤销全部令牌。
     */
    @Override
    @Transactional
    public void resetPassword(MemberPasswordResetRequest request) {
        String email = MemberValues.email(request.email());
        codes.consume(email, MemberCodePurpose.RESET_PASSWORD, request.code());
        MemberUserEntity user = findByEmail(email);
        if (user == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "验证码错误或已失效");
        }
        users.update(null, Wrappers.<MemberUserEntity>lambdaUpdate()
                .set(MemberUserEntity::getPasswordHash, passwordEncoder.encode(request.newPassword()))
                .set(MemberUserEntity::getUpdatedAt, MemberValues.now())
                .eq(MemberUserEntity::getId, user.getId()));
        sessions.revokeAll(user.getId(), MemberOfflineReason.REVOKED);
        attemptGuard.clear(ATTEMPT_PREFIX + email);
    }

    /**
     * 退出当前设备。
     */
    @Override
    public void logout() {
        AuthenticatedMember member = CurrentMember.require();
        sessions.logout(member.deviceId(), CurrentMember.token().orElse(null));
    }

    /**
     * 未开放注册时返回 403。
     */
    private void assertRegistrationOpen() {
        if (!features.current().registrationOpen()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "暂未开放注册");
        }
    }

    /**
     * 按规范化邮箱查询账号。
     */
    private MemberUserEntity findByEmail(String email) {
        return users.selectOne(Wrappers.<MemberUserEntity>lambdaQuery().eq(MemberUserEntity::getEmail, email));
    }

    /**
     * 使用邮箱前缀作为默认昵称。
     */
    private String defaultNickname(String email) {
        String prefix = email.substring(0, Math.max(email.indexOf('@'), 1));
        return prefix.length() > MemberValues.NICKNAME_MAX_LENGTH
                ? prefix.substring(0, MemberValues.NICKNAME_MAX_LENGTH) : prefix;
    }

    /**
     * 组装登录响应。
     */
    private MemberLoginResponse response(MemberToken token, MemberUserEntity user) {
        return new MemberLoginResponse(token.value(), TOKEN_TYPE, token.expiresInSeconds(),
                converter.profile(user, features.current().vipRequired()));
    }
}
