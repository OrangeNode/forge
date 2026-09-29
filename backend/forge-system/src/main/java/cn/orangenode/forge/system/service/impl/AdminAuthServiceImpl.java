package cn.orangenode.forge.system.service.impl;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;

import cn.orangenode.forge.core.exception.BusinessException;
import cn.orangenode.forge.core.response.ErrorCode;
import cn.orangenode.forge.framework.security.AdminToken;
import cn.orangenode.forge.framework.security.AdminTokenService;
import cn.orangenode.forge.framework.security.LoginAttemptGuard;
import cn.orangenode.forge.system.converter.AdminAuthConverter;
import cn.orangenode.forge.system.credential.AdminCredentialPolicy;
import cn.orangenode.forge.system.entity.SysAdminEntity;
import cn.orangenode.forge.system.enums.AdminAccountStatus;
import cn.orangenode.forge.system.mapper.SysAdminMapper;
import cn.orangenode.forge.system.mapper.SysMenuMapper;
import cn.orangenode.forge.system.request.AdminLoginRequest;
import cn.orangenode.forge.system.response.AdminLoginResponse;
import cn.orangenode.forge.system.response.AdminMenuResponse;
import cn.orangenode.forge.system.response.AdminProfileResponse;
import cn.orangenode.forge.system.service.AdminAuthService;
import cn.orangenode.forge.system.service.AdminAuthorityService;

import lombok.extern.slf4j.Slf4j;

/**
 * 管理员认证用例实现。
 *
 * <p>登录失败对“账号不存在”“密码错误”“账号已停用”返回同一提示与同一 code，
 * 不向调用方暴露账号是否存在；失败同时计入 Redis 限流，达到上限后返回 429。</p>
 *
 * <p>退出只撤销当前令牌；账号停用与改密需要撤销该账号全部会话，
 * 由后续管理接口调用令牌会话的批量撤销能力。</p>
 */
@Slf4j
@Service
public class AdminAuthServiceImpl implements AdminAuthService {

    /**
     * 登录失败的统一提示，不区分具体原因。
     */
    private static final String LOGIN_FAILURE_MESSAGE = "用户名或密码错误";

    /**
     * 登录失效的统一提示，与安全过滤链的认证失败提示保持一致。
     */
    private static final String UNAUTHORIZED_MESSAGE = "登录已失效，请重新登录";

    /**
     * 管理员账号数据访问。
     */
    private final SysAdminMapper adminMapper;

    /**
     * 菜单数据访问。
     */
    private final SysMenuMapper menuMapper;

    /**
     * 密码编码器，只用于校验，不保存明文。
     */
    private final PasswordEncoder passwordEncoder;

    /**
     * 令牌会话，负责签发与撤销。
     */
    private final AdminTokenService tokenService;

    /**
     * 登录失败限流。
     */
    private final LoginAttemptGuard loginAttemptGuard;

    /**
     * 权限与角色解析。
     */
    private final AdminAuthorityService authorityService;

    /**
     * 认证相关转换器。
     */
    private final AdminAuthConverter converter;

    /**
     * 构造管理员认证用例实现。
     *
     * @param adminMapper        管理员账号数据访问
     * @param menuMapper         菜单数据访问
     * @param passwordEncoder    密码编码器
     * @param tokenService       令牌会话
     * @param loginAttemptGuard  登录失败限流
     * @param authorityService   权限与角色解析
     * @param converter          认证相关转换器
     */
    public AdminAuthServiceImpl(SysAdminMapper adminMapper, SysMenuMapper menuMapper, PasswordEncoder passwordEncoder,
            AdminTokenService tokenService, LoginAttemptGuard loginAttemptGuard,
            AdminAuthorityService authorityService, AdminAuthConverter converter) {
        this.adminMapper = adminMapper;
        this.menuMapper = menuMapper;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.loginAttemptGuard = loginAttemptGuard;
        this.authorityService = authorityService;
        this.converter = converter;
    }

    /**
     * 校验凭据并签发令牌。
     *
     * @param request 登录入参
     * @return 令牌与当前管理员基础信息
     * @throws BusinessException 凭据无效或账号不可用时抛出 401，触发限流时抛出 429
     */
    @Override
    public AdminLoginResponse login(AdminLoginRequest request) {
        String username = AdminCredentialPolicy.normalizeUsername(request.username());
        if (username == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, LOGIN_FAILURE_MESSAGE);
        }
        loginAttemptGuard.assertAllowed(username);

        SysAdminEntity admin = findByUsername(username);
        if (!credentialsMatch(admin, request.password())) {
            loginAttemptGuard.recordFailure(username);
            log.warn("管理员登录失败，username={}", username);
            throw new BusinessException(ErrorCode.UNAUTHORIZED, LOGIN_FAILURE_MESSAGE);
        }

        markLoginSuccess(admin);
        AdminToken token = tokenService.issue(admin.getId());
        loginAttemptGuard.clear(username);
        return converter.toLoginResponse(token, admin);
    }

    /**
     * 退出登录并撤销当前令牌。
     *
     * @param token 当前请求使用的令牌
     */
    @Override
    public void logout(String token) {
        tokenService.revoke(token);
    }

    /**
     * 查询当前管理员身份与权限。
     *
     * @param adminId 当前管理员 ID
     * @return 身份与权限响应
     * @throws BusinessException 账号不存在或已停用时抛出 401
     */
    @Override
    public AdminProfileResponse currentProfile(Long adminId) {
        SysAdminEntity admin = requireActiveAdmin(adminId);
        return converter.toProfileResponse(admin, authorityService.loadPermissionCodes(adminId));
    }

    /**
     * 查询当前管理员可见菜单。
     *
     * @param adminId 当前管理员 ID
     * @return 菜单树
     * @throws BusinessException 账号不存在或已停用时抛出 401
     */
    @Override
    public List<AdminMenuResponse> currentMenus(Long adminId) {
        requireActiveAdmin(adminId);
        if (authorityService.isSuperAdmin(adminId)) {
            return converter.toMenuTree(menuMapper.selectAllMenus());
        }
        return converter.toMenuTree(menuMapper.selectMenusByAdminId(adminId));
    }

    /**
     * 按规范化用户名查询账号。
     *
     * @param username 规范化后的用户名
     * @return 账号实体，不存在时返回 {@code null}
     */
    private SysAdminEntity findByUsername(String username) {
        return adminMapper.selectOne(Wrappers.<SysAdminEntity>lambdaQuery()
                .eq(SysAdminEntity::getUsername, username));
    }

    /**
     * 校验密码与账号状态。
     *
     * <p>账号不存在、停用或密码不匹配都返回 {@code false}，由调用方统一处理为同一种失败。</p>
     *
     * @param admin    账号实体，允许为 {@code null}
     * @param password 登录密码明文
     * @return 凭据有效且账号启用时返回 {@code true}
     */
    private boolean credentialsMatch(SysAdminEntity admin, String password) {
        if (admin == null || password == null || !AdminAccountStatus.isEnabled(admin.getStatus())) {
            return false;
        }
        String passwordHash = admin.getPasswordHash();
        return passwordHash != null && passwordEncoder.matches(password, passwordHash);
    }

    /**
     * 记录登录成功时间。
     *
     * @param admin 已通过校验的账号实体
     */
    private void markLoginSuccess(SysAdminEntity admin) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        SysAdminEntity update = new SysAdminEntity();
        update.setId(admin.getId());
        update.setLastLoginAt(now);
        update.setUpdatedAt(now);
        adminMapper.updateById(update);
    }

    /**
     * 查询处于启用状态的账号，供当前身份用例使用。
     *
     * @param adminId 管理员 ID
     * @return 账号实体
     * @throws BusinessException 账号不存在或已停用时抛出 401
     */
    private SysAdminEntity requireActiveAdmin(Long adminId) {
        SysAdminEntity admin = adminId == null ? null : adminMapper.selectById(adminId);
        if (admin == null || !AdminAccountStatus.isEnabled(admin.getStatus())) {
            log.warn("当前身份的账号不存在或已停用，adminId={}", adminId);
            throw new BusinessException(ErrorCode.UNAUTHORIZED, UNAUTHORIZED_MESSAGE);
        }
        return admin;
    }
}
