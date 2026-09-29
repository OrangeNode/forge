package cn.orangenode.forge.framework.security;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import cn.orangenode.forge.core.exception.DependencyUnavailableException;
import cn.orangenode.forge.core.response.ErrorCode;
import cn.orangenode.forge.framework.web.error.WebErrorWriter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * Bearer 令牌认证过滤器。
 *
 * <p>请求带令牌时按顺序完成四件事：校验令牌会话、读取账号当前状态、读取账号当前权限、
 * 写入安全上下文。账号停用、令牌被撤销或版本落后时都不建立认证，
 * 由安全过滤链的认证入口统一写出 HTTP 200 + body.code=401。</p>
 *
 * <p>令牌会话与账号状态都不缓存，因此停用、改密与权限调整在下一次请求立即生效。</p>
 *
 * <p>Redis 不可用时直接写出 body.code=503 并结束请求，不放行也不降级为匿名继续执行业务。
 * 本过滤器由安全过滤链装配，不声明为组件，避免被 Servlet 容器再次注册到过滤链之外。</p>
 */
@Slf4j
public class BearerTokenAuthenticationFilter extends OncePerRequestFilter {

    /**
     * 令牌会话校验入口。
     */
    private final AdminTokenService tokenService;

    /**
     * 账号与权限查询端口，由账号模块实现。
     */
    private final AdminAccountDirectory accountDirectory;

    /**
     * 统一错误响应写出组件，与认证入口、全局异常处理器共用。
     */
    private final WebErrorWriter errorWriter;

    /**
     * 构造 Bearer 令牌认证过滤器。
     *
     * @param tokenService     令牌会话校验入口
     * @param accountDirectory 账号与权限查询端口
     * @param errorWriter      统一错误响应写出组件
     */
    public BearerTokenAuthenticationFilter(AdminTokenService tokenService, AdminAccountDirectory accountDirectory,
            WebErrorWriter errorWriter) {
        this.tokenService = tokenService;
        this.accountDirectory = accountDirectory;
        this.errorWriter = errorWriter;
    }

    /**
     * 解析令牌并建立认证上下文，无法建立时保持匿名。
     *
     * @param request     当前 HTTP 请求
     * @param response    当前 HTTP 响应
     * @param filterChain 后续过滤链
     * @throws ServletException 后续过滤器抛出异常时透传
     * @throws IOException      读写请求响应失败时透传
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Optional<String> token = BearerTokens.resolve(request.getHeader(HttpHeaders.AUTHORIZATION));
        if (token.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }

        Optional<Long> adminId;
        try {
            adminId = tokenService.resolveAdminId(token.get());
        } catch (RedisConnectionFailureException | DependencyUnavailableException failure) {
            log.error("令牌校验依赖不可用，拒绝请求，路径={}", request.getRequestURI(), failure);
            errorWriter.write(request, response, ErrorCode.SERVICE_UNAVAILABLE, "服务依赖暂不可用，请稍后重试");
            return;
        }
        if (adminId.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }

        Optional<AdminAccountView> account = accountDirectory.findById(adminId.get());
        if (account.isEmpty()) {
            log.warn("令牌对应的管理员已不存在，adminId={}", adminId.get());
            filterChain.doFilter(request, response);
            return;
        }
        if (!account.get().enabled()) {
            log.warn("管理员已停用，拒绝其令牌，adminId={}", adminId.get());
            filterChain.doFilter(request, response);
            return;
        }

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(toPrincipal(account.get()), token.get(),
                toAuthorities(accountDirectory.loadAuthorityCodes(adminId.get()))));
        SecurityContextHolder.setContext(context);
        filterChain.doFilter(request, response);
    }

    /**
     * 把账号视图转换为认证主体。
     *
     * @param account 账号视图
     * @return 认证主体
     */
    private AuthenticatedAdmin toPrincipal(AdminAccountView account) {
        return new AuthenticatedAdmin(account.id(), account.username(), account.displayName());
    }

    /**
     * 把权限代码转换为 Spring Security 权限集合。
     *
     * @param authorityCodes 权限代码集合
     * @return 权限集合
     */
    private List<GrantedAuthority> toAuthorities(List<String> authorityCodes) {
        if (authorityCodes == null || authorityCodes.isEmpty()) {
            return List.of();
        }
        return authorityCodes.stream()
                .filter(code -> code != null && !code.isBlank())
                .map(code -> (GrantedAuthority) new SimpleGrantedAuthority(code))
                .toList();
    }
}
