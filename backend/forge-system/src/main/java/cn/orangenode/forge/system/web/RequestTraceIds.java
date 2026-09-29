package cn.orangenode.forge.system.web;

import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import cn.orangenode.forge.framework.web.TraceIdFilter;

/**
 * 当前请求追踪编号解析。
 *
 * <p>登录日志与统一响应体使用同一个追踪编号：编号由框架的追踪过滤器在请求进入时写入请求属性，
 * 此处只读取，不生成也不接受客户端提交的编号。</p>
 */
public final class RequestTraceIds {

    /**
     * 工具类不允许实例化。
     */
    private RequestTraceIds() {
    }

    /**
     * 读取当前请求的追踪编号。
     *
     * @return 追踪编号，无请求上下文或编号未分配时返回 {@code null}
     */
    public static String current() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletAttributes) {
            return TraceIdFilter.currentTraceId(servletAttributes.getRequest());
        }
        return null;
    }
}
