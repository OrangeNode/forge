package cn.orangenode.forge.member.service.impl;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Locale;

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
import cn.orangenode.forge.framework.redis.ForgeRedisTemplate;
import cn.orangenode.forge.framework.security.LoginAttemptGuard;
import cn.orangenode.forge.member.config.MemberProperties;
import cn.orangenode.forge.member.converter.MemberConverter;
import cn.orangenode.forge.member.entity.MemberUserEntity;
import cn.orangenode.forge.member.mapper.MemberUserMapper;
import cn.orangenode.forge.member.request.MemberLoginRequest;
import cn.orangenode.forge.member.request.MemberRegisterRequest;
import cn.orangenode.forge.member.request.MemberVipExtendRequest;
import cn.orangenode.forge.member.response.MemberLoginResponse;
import cn.orangenode.forge.member.service.MemberActivationService;
import cn.orangenode.forge.member.service.MemberAuthService;
import cn.orangenode.forge.member.service.MemberManagementService;
import cn.orangenode.forge.member.service.MemberSessionService;
import cn.orangenode.forge.member.support.MemberStatus;
import cn.orangenode.forge.member.support.MemberValues;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 普通用户认证实现。
 *
 * <p>登录失败计数复用基础层限流组件，键名加普通用户前缀，与管理员用户名互不影响；
 * 账号不存在与密码错误返回同一句 401 提示。用户名注册不经过邮箱验证，按来源地址限制每日注册次数。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemberAuthServiceImpl implements MemberAuthService {
    private static final String ATTEMPT_PREFIX = "member:";
    private static final String SOURCE_SCOPE = "member";
    private static final String REGISTER_IP_KEY = "member:register-ip:";
    private static final String TOKEN_TYPE = "Bearer";
    private static final String BAD_CREDENTIALS = "用户名或密码错误";
    private static final Duration DAY = Duration.ofDays(1);
    private final MemberUserMapper users;
    private final MemberSessionService sessions;
    private final PasswordEncoder passwordEncoder;
    private final LoginAttemptGuard attemptGuard;
    private final AppFeatureResolver features;
    private final MemberConverter converter;
    private final ForgeRedisTemplate redis;
    private final MemberProperties properties;
    private final MemberActivationService activations;
    private final MemberManagementService members;

    /**
     * 检查注册开关、激活码、用户名占用与来源地址每日次数后创建账号、核销激活码、按激活码的会员天数从现在起开通 VIP 并登录当前设备；
     * 并发重复注册由唯一键兜底返回 409，激活码被并发抢用时核销失败返回 409 并回滚账号。
     */
    @Override
    @Transactional
    public MemberLoginResponse register(MemberRegisterRequest request, String clientIp) {
        assertRegistrationOpen();
        Long codeId = activations.check(request.activationCode(), clientIp);
        String username = MemberValues.username(request.username());
        if (findByUsername(username) != null) {
            throw new BusinessException(ErrorCode.CONFLICT, "该用户名已被注册");
        }
        if (clientIp != null) {
            String key = REGISTER_IP_KEY + LocalDate.now(ZoneOffset.UTC) + ":" + clientIp;
            if (redis.increment(key, DAY) > properties.getRegister().getIpDailyLimit()) {
                throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS, "注册过于频繁，请明天再试");
            }
        }
        MemberUserEntity user = new MemberUserEntity();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        String nickname = MemberValues.clean(request.nickname());
        user.setNickname(nickname == null ? username : nickname);
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
            throw new BusinessException(ErrorCode.CONFLICT, "该用户名已被注册");
        }
        Integer vipDays = activations.consume(codeId, user.getId(), username, clientIp);
        if (vipDays != null) {
            members.extendVip(user.getId(), new MemberVipExtendRequest(vipDays, "激活码注册开通 " + vipDays + " 天"));
        }
        log.info("普通用户注册成功，用户 ID={}，用户名={}，激活码 ID={}", user.getId(), username, codeId);
        MemberToken token = sessions.open(user.getId(), request.deviceCode(), request.deviceName().strip(), clientIp);
        return response(token, users.selectById(user.getId()));
    }

    /**
     * 校验凭据与账号状态后在当前设备签发令牌。
     */
    @Override
    @Transactional
    public MemberLoginResponse login(MemberLoginRequest request, String clientIp) {
        String username = MemberValues.username(request.username());
        String attemptKey = ATTEMPT_PREFIX + (username == null ? "" : username.toLowerCase(Locale.ROOT));
        attemptGuard.assertSourceAllowed(SOURCE_SCOPE, clientIp);
        attemptGuard.assertAllowed(attemptKey);
        MemberUserEntity user = findByUsername(username);
        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            attemptGuard.recordSourceFailure(SOURCE_SCOPE, clientIp);
            attemptGuard.recordFailure(attemptKey);
            throw new BusinessException(ErrorCode.UNAUTHORIZED, BAD_CREDENTIALS);
        }
        if (!MemberStatus.ENABLED.name().equals(user.getStatus())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "账号已停用，请联系客服");
        }
        attemptGuard.clear(attemptKey);
        users.update(null, Wrappers.<MemberUserEntity>lambdaUpdate()
                .set(MemberUserEntity::getLastLoginAt, MemberValues.now())
                .set(MemberUserEntity::getLastLoginIp, clientIp)
                .eq(MemberUserEntity::getId, user.getId()));
        MemberToken token = sessions.open(user.getId(), request.deviceCode(), request.deviceName().strip(), clientIp);
        return response(token, users.selectById(user.getId()));
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
     * 按用户名查询账号，比较不区分大小写（由表排序规则保证）。
     */
    private MemberUserEntity findByUsername(String username) {
        if (username == null) {
            return null;
        }
        return users.selectOne(Wrappers.<MemberUserEntity>lambdaQuery().eq(MemberUserEntity::getUsername, username));
    }

    /**
     * 组装登录响应。
     */
    private MemberLoginResponse response(MemberToken token, MemberUserEntity user) {
        return new MemberLoginResponse(token.value(), TOKEN_TYPE, token.expiresInSeconds(),
                converter.profile(user, features.current().vipRequired()));
    }
}
