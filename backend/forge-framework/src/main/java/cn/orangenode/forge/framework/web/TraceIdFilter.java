package cn.orangenode.forge.framework.web;

import java.io.IOException;
import java.util.UUID;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 追踪编号过滤器。
 *
 * <p>为每个请求生成追踪编号，写入响应头与请求属性，供统一响应体和服务器日志关联使用。
 * 只写入随机值，不接收客户端提交的追踪编号，避免调用方伪造日志关联线索。</p>
 *
 * <p>响应头名称与是否启用来自配置，装配入口见 {@code WebTraceConfig}；
 * 本类不声明为组件，避免与配置条件冲突。</p>
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    /**
     * 请求属性中的追踪编号键名。
     */
    public static final String TRACE_ID_ATTRIBUTE = TraceIdFilter.class.getName() + ".traceId";

    /**
     * 追踪编号响应头名称，来自配置。
     */
    private final String traceHeaderName;

    /**
     * 构造追踪编号过滤器。
     *
     * @param traceHeaderName 追踪编号响应头名称
     */
    public TraceIdFilter(String traceHeaderName) {
        this.traceHeaderName = traceHeaderName;
    }

    /**
     * 为当前请求分配追踪编号并写入响应头，随后继续过滤链。
     *
     * @param request     当前 HTTP 请求
     * @param response    当前 HTTP 响应
     * @param filterChain 后续过滤链
     * @throws ServletException 后续过滤器或 Servlet 抛出异常时透传
     * @throws IOException      写响应头失败时透传
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String traceId = newTraceId();
        request.setAttribute(TRACE_ID_ATTRIBUTE, traceId);
        response.setHeader(traceHeaderName, traceId);
        filterChain.doFilter(request, response);
    }

    /**
     * 生成 32 位无连字符的随机追踪编号。
     *
     * <p>长度由 UUID 决定，属于协议不变式，不做成配置项。</p>
     *
     * @return 追踪编号字符串
     */
    private String newTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
