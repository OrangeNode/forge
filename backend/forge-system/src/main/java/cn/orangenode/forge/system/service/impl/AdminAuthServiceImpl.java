package cn.orangenode.forge.system.service.impl;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;

import cn.orangenode.forge.core.exception.BusinessException;
import cn.orangenode.forge.core.response.ErrorCode;
import cn.orangenode.forge.framework.audit.LoginLogRecord;
import cn.orangenode.forge.framework.audit.LoginLogRecorder;
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
import cn.orangenode.forge.system.web.ClientIpResolver;
import cn.orangenode.forge.system.web.RequestTraceIds;

import lombok.extern.slf4j.Slf4j;

/**
 * 管理员认证用例实现。
 *
 * <p>登录失败对“账号不存在”“密码错误”“账号已停用”返回同一提示与同一 code，
 * 不向调用方暴露账号是否存在；失败同时计入 Redis 限流，达到上限后返回 429。
 * 服务端按真实原因分别写入登录日志的原因分类（凭据错误、账号停用、触发限流），
 * 日志只供管理员排查，不进入响应。</p>
 *
 * <p>登录日志通过 framework 的 {@link LoginLogRecorder} 端口写入，实现由审计模块提供：
 * 本模块不依赖审计模块的类，也不直接写审计表。日志写入失败不改变登录结论——
 * 认证是安全关键路径，审计属于旁路能力，不能因为审计库不可用让已校验成功的登录失败。</p>
 *
 * <p>退出只撤销当前令牌；账号停用与改密需要撤销该账号全部会话，由管理员管理接口调用
 * 令牌会话的批量撤销能力。</p>
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
     * 登录日志的结果代码：成功。
     */
    private static final String LOGIN_RESULT_SUCCESS = "success";

    /**
     * 登录日志的结果代码：失败。
     */
    private static final String LOGIN_RESULT_FAILURE = "failure";

    /**
     * 登录日志的失败原因：凭据错误或账号已停用。
     */
    private static final String REASON_CREDENTIALS = "credentials";

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
     * 登录日志记录端口，由审计模块实现。
     *
     * <p>用 {@link ObjectProvider} 按接口取用而不是声明强依赖：审计模块属于旁路能力，
     * 未装配或未启动时登录仍应按凭据校验结果正常工作，不能因为缺少审计实现让整个应用起不来；
     * 装配了实现时按接口注入并真实写库，不 import 审计模块的任何类。</p>
     */
    private final ObjectProvider<LoginLogRecorder> loginLogRecorderProvider;

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
     * @param loginLogRecorderProvider 登录日志记录端口提供者
     */
    public AdminAuthServiceImpl(SysAdminMapper adminMapper, SysMenuMapper menuMapper, PasswordEncoder passwordEncoder,
            AdminTokenService tokenService, LoginAttemptGuard loginAttemptGuard,
            AdminAuthorityService authorityService, AdminAuthConverter converter,
            ObjectProvider<LoginLogRecorder> loginLogRecorderProvider) {
        this.adminMapper = adminMapper;
        this.menuMapper = menuMapper;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.loginAttemptGuard = loginAttemptGuard;
        this.authorityService = authorityService;
        this.converter = converter;
        this.loginLogRecorderProvider = loginLogRecorderProvider;
    }

    /**
     * 校验凭据并签发令牌。
     *
     * <p>四条路径分别写入登录日志：限流拒绝（rate_limited）、账号不存在或密码错误（credentials）、
     * 账号已停用（disabled）、凭据有效（success）。来源地址与追踪编号从当前请求上下文读取，
     * 与响应体和操作审计使用同一个追踪编号，便于按 traceId 串联一次登录尝试。</p>
     *
     * @param request 登录入参
     * @return 令牌与当前管理员基础信息
     * @throws BusinessException 凭据无效或账号不可用时抛出 401，触发限流时抛出 429
     */
    @Override
    public AdminLoginResponse login(AdminLoginRequest request) {
        String username = AdminCredentialPolicy.normalizeUsername(request.username());
        if (username == null) {
            writeLoginLog(null, LOGIN_RESULT_FAILURE, REASON_CREDENTIALS);
            throw new BusinessException(ErrorCode.UNAUTHORIZED, LOGIN_FAILURE_MESSAGE);
        }
        try {
            loginAttemptGuard.assertAllowed(username);
        } catch (BusinessException failure) {
            writeLoginLog(username, LOGIN_RESULT_FAILURE, "rate_limited");
            throw failure;
        }

        SysAdminEntity admin = findByUsername(username);
        String failureReason = resolveFailureReason(admin, request.password());
        if (failureReason != null) {
            loginAttemptGuard.recordFailure(username);
            writeLoginLog(username, LOGIN_RESULT_FAILURE, failureReason);
            throw new BusinessException(ErrorCode.UNAUTHORIZED, LOGIN_FAILURE_MESSAGE);
        }

        markLoginSuccess(admin);
        AdminToken token = tokenService.issue(admin.getId());
        loginAttemptGuard.clear(username);
        writeLoginLog(username, LOGIN_RESULT_SUCCESS, null);
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
     * 判定登录失败的具体原因。
     *
     * <p>原因只用于服务端登录日志：对外响应始终是同一句提示与同一个 code，
     * 不因为区分“账号不存在”与“密码错误”而泄露账号是否存在。
     * 账号不存在与密码错误统一归类为凭据错误，账号停用单独归类为 disabled。</p>
     *
     * @param admin    按用户名查到的账号实体，允许为 {@code null}
     * @param password 登录密码明文，允许为 {@code null}
     * @return 失败原因分类，凭据有效时返回 {@code null}
     */
    private String resolveFailureReason(SysAdminEntity admin, String password) {
        if (admin == null || password == null) {
            return REASON_CREDENTIALS;
        }
        if (!AdminAccountStatus.isEnabled(admin.getStatus())) {
            return "disabled";
        }
        String passwordHash = admin.getPasswordHash();
        boolean matched = passwordHash != null && passwordEncoder.matches(password, passwordHash);
        return matched ? null : REASON_CREDENTIALS;
    }

    /**
     * 写入一条登录日志。
     *
     * <p>写入异常只记录错误日志，不改变登录结论；来源地址与追踪编号从当前请求上下文读取，
     * 无请求上下文时为空值，由审计模块按可空列保存。</p>
     *
     * @param username 规范化后的用户名，允许为 {@code null}
     * @param result   结果代码，{@code success} 或 {@code failure}
     * @param reason   失败原因分类，成功时传 {@code null}
     */
    private void writeLoginLog(String username, String result, String reason) {
        LoginLogRecord record = new LoginLogRecord(username, result, reason, ClientIpResolver.resolve(),
                RequestTraceIds.current());
        LoginLogRecorder recorder = loginLogRecorderProvider.getIfAvailable();
        if (recorder == null) {
            log.debug("审计模块未装配，跳过登录日志写入，username={}，result={}", username, result);
        } else {
            try {
                recorder.record(record);
            } catch (RuntimeException failure) {
                log.error("登录日志写入失败，username={}，result={}，reason={}", username, result, reason, failure);
            }
        }
        if (LOGIN_RESULT_SUCCESS.equals(result)) {
            log.info("管理员登录成功，username={}", username);
        } else {
            log.warn("管理员登录失败，username={}，reason={}", username, reason);
        }
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
