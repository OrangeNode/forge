package cn.orangenode.forge.framework.web.error;

import java.io.IOException;
import java.util.UUID;

import org.springframework.stereotype.Component;

import cn.orangenode.forge.core.response.ApiResponse;
import cn.orangenode.forge.framework.web.TraceIdFilter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

/**
 * 统一错误写出组件。
 *
 * <p>ControllerAdvice、Security 的认证与拒绝访问处理器、框架默认错误出口都通过本组件写出响应，
 * 保证失败响应同样是 HTTP 200，并携带 {@code code}、{@code message}、{@code data}、{@code traceId} 四字段。</p>
 *
 * <p>HTTP 200 不代表放行：被拒绝的请求在写出错误响应后立即返回，不再进入后续业务处理。
 * 错误响应中的追踪编号优先取 {@link TraceIdFilter} 已分配的编号，保证与响应头一致。</p>
 */
@Component
public class WebErrorWriter {

    /**
     * 失败响应使用的 JSON 映射器，与接口正常响应保持同一装配方式。
     */
    private final JsonMapper jsonMapper;

    /**
     * 构造统一错误写出组件。
     *
     * @param jsonMapper 应用统一使用的 JSON 映射器
     */
    public WebErrorWriter(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    /**
     * 写出不含结构化数据的失败响应。
     *
     * @param request  当前 HTTP 请求
     * @param response 当前 HTTP 响应
     * @param code     失败结果码
     * @param message  面向调用者的中文提示
     */
    public void write(HttpServletRequest request, HttpServletResponse response, int code, String message) {
        write(request, response, ApiResponse.failureWithTrace(code, message, resolveTraceId(request)));
    }

    /**
     * 写出携带结构化错误数据的失败响应。
     *
     * @param request  当前 HTTP 请求
     * @param response 当前 HTTP 响应
     * @param code     失败结果码
     * @param message  面向调用者的中文提示
     * @param data     结构化错误数据，例如字段错误列表
     */
    public void write(HttpServletRequest request, HttpServletResponse response, int code, String message, Object data) {
        write(request, response, ApiResponse.failureWithTrace(code, message, data, resolveTraceId(request)));
    }

    /**
     * 读取当前请求的追踪编号，缺失时生成一个，避免响应体出现空值。
     *
     * @param request 当前 HTTP 请求
     * @return 非空追踪编号
     */
    public String resolveTraceId(HttpServletRequest request) {
        String traceId = TraceIdFilter.currentTraceId(request);
        return traceId == null ? UUID.randomUUID().toString().replace("-", "") : traceId;
    }

    /**
     * 以 HTTP 200 与 {@code application/json} 输出统一响应体。
     *
     * @param request 当前 HTTP 请求
     * @param response 当前 HTTP 响应
     * @param body     统一响应体
     */
    private void write(HttpServletRequest request, HttpServletResponse response, ApiResponse<?> body) {
        if (response.isCommitted()) {
            return;
        }
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("application/json;charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        try {
            response.getWriter().write(jsonMapper.writeValueAsString(body));
            response.flushBuffer();
        } catch (IOException failure) {
            throw new ErrorResponseWriteException("统一错误响应写出失败", failure);
        }
    }
}
