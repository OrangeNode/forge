package cn.orangenode.forge.framework.member;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import cn.orangenode.forge.core.exception.DependencyUnavailableException;
import cn.orangenode.forge.core.response.ErrorCode;
import cn.orangenode.forge.framework.security.BearerTokens;
import cn.orangenode.forge.framework.web.ClientAddresses;
import cn.orangenode.forge.framework.web.error.WebErrorWriter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * 普通用户 Bearer 令牌认证过滤器。
 *
 * <p>只作用于普通用户接口前缀；管理员令牌在该前缀下不会被识别。账号停用、令牌被撤销、
 * 会话版本落后时保持匿名，由认证入口统一写出 body.code=401。Redis 不可用时写出 503 并结束请求。</p>
 *
 * <p>本过滤器由安全过滤链装配，不声明为组件，避免被容器重复注册。</p>
 */
@Slf4j
public class MemberBearerAuthenticationFilter extends OncePerRequestFilter {

    /**
     * 普通用户接口路径前缀。
     */
    private final String pathPrefix;

    /**
     * 普通用户令牌会话。
     */
    private final MemberTokenService tokenService;

    /**
     * 普通用户账号目录端口。
     */
    private final MemberAccountDirectory directory;

    /**
     * 统一错误响应写出组件。
     */
    private final WebErrorWriter errorWriter;

    /**
     * 构造普通用户认证过滤器。
     *
     * @param pathPrefix   普通用户接口路径前缀
     * @param tokenService 普通用户令牌会话
     * @param directory    普通用户账号目录端口
     * @param errorWriter  统一错误响应写出组件
     */
    public MemberBearerAuthenticationFilter(String pathPrefix, MemberTokenService tokenService,
            MemberAccountDirectory directory, WebErrorWriter errorWriter) {
        this.pathPrefix = pathPrefix;
        this.tokenService = tokenService;
        this.directory = directory;
        this.errorWriter = errorWriter;
    }

    /**
     * 只在普通用户接口前缀下执行。
     *
     * @param request 当前请求
     * @return 不在前缀下时为 {@code true}
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(pathPrefix);
    }

    /**
     * 校验令牌并建立普通用户认证上下文。
     *
     * @param request     当前请求
     * @param response    当前响应
     * @param filterChain 后续过滤链
     * @throws ServletException 后续过滤器异常
     * @throws IOException      读写失败
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Optional<String> token = BearerTokens.resolve(request.getHeader(HttpHeaders.AUTHORIZATION));
        if (token.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }
        try {
            Optional<MemberSessionTicket> ticket = tokenService.resolve(token.get());
            if (ticket.isPresent()) {
                authenticate(request, token.get(), ticket.get());
            }
        } catch (RedisConnectionFailureException | DependencyUnavailableException failure) {
            log.error("普通用户令牌校验依赖不可用，拒绝请求，路径={}", request.getRequestURI(), failure);
            errorWriter.write(request, response, ErrorCode.SERVICE_UNAVAILABLE, "服务依赖暂不可用，请稍后重试");
            return;
        }
        filterChain.doFilter(request, response);
    }

    /**
     * 核对账号状态与会话版本，通过后写入安全上下文。
     *
     * @param request 当前请求
     * @param token   令牌明文，作为认证凭据保留供退出使用
     * @param ticket  令牌校验结果
     */
    private void authenticate(HttpServletRequest request, String token, MemberSessionTicket ticket) {
        Optional<MemberAccountView> account = directory.findById(ticket.memberId());
        if (account.isEmpty() || !account.get().enabled()
                || account.get().sessionVersion() != ticket.sessionVersion()) {
            log.info("普通用户令牌对应账号不可用或会话已撤销，memberId={}", ticket.memberId());
            tokenService.revoke(token);
            return;
        }
        if (ticket.renewed()) {
            directory.recordActivity(ticket.deviceId(), ClientAddresses.resolve(request));
        }
        MemberAccountView view = account.get();
        AuthenticatedMember principal = new AuthenticatedMember(view.id(), view.email(), view.nickname(),
                view.vipExpireAt(), ticket.deviceId());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(principal, token, List.of()));
        SecurityContextHolder.setContext(context);
    }
}
