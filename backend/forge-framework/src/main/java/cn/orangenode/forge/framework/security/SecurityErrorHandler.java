package cn.orangenode.forge.framework.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

import cn.orangenode.forge.core.response.ErrorCode;
import cn.orangenode.forge.framework.web.error.WebErrorWriter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * 安全过滤链的统一错误出口。
 *
 * <p>同时实现认证入口与拒绝访问处理器：未认证与无权限都写出 HTTP 200 + 对应 {@code body.code}
 * （分别为 401 与 403），不使用容器默认的 401 状态、错误页或登录重定向。</p>
 *
 * <p>返回 HTTP 200 不代表放行：处理器写出错误响应后过滤链立即结束，请求不会进入 Controller 与业务服务。</p>
 */
@Slf4j
public class SecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    /**
     * 统一错误响应写出组件，与其他错误出口共用。
     */
    private final WebErrorWriter errorWriter;

    /**
     * 构造安全错误出口处理器。
     *
     * @param errorWriter 统一错误响应写出组件
     */
    public SecurityErrorHandler(WebErrorWriter errorWriter) {
        this.errorWriter = errorWriter;
    }

    /**
     * 处理未认证或令牌无效的请求。
     *
     * @param request   当前 HTTP 请求
     * @param response  当前 HTTP 响应
     * @param exception 认证异常，只用于服务端日志
     */
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException exception) {
        log.warn("请求未通过认证，traceId={}，路径={}", errorWriter.resolveTraceId(request), request.getRequestURI());
        errorWriter.write(request, response, ErrorCode.UNAUTHORIZED, "登录已失效，请重新登录");
    }

    /**
     * 处理已认证但缺少权限的请求。
     *
     * @param request   当前 HTTP 请求
     * @param response  当前 HTTP 响应
     * @param exception 拒绝访问异常，只用于服务端日志
     */
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException exception) {
        log.warn("请求被拒绝访问，traceId={}，路径={}", errorWriter.resolveTraceId(request), request.getRequestURI());
        errorWriter.write(request, response, ErrorCode.FORBIDDEN, "没有访问该资源的权限");
    }
}
