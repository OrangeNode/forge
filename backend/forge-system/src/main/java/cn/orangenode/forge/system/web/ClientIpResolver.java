package cn.orangenode.forge.system.web;

import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 来源地址解析。
 *
 * <p>登录日志需要记录来源地址。应用位于反向代理之后时，{@code request.getRemoteAddr()} 只会得到代理地址，
 * 因此先读取 {@code X-Forwarded-For} 中代表客户端的第一段；该请求头由代理覆盖写入，
 * 只用于日志留痕，不参与任何授权判断，即使被伪造也不影响安全结论。</p>
 *
 * <p>当前请求通过 {@link RequestContextHolder} 获取，与操作审计切面取追踪编号的方式保持一致，
 * 业务用例因此不需要把 {@code HttpServletRequest} 一路透传。</p>
 */
public final class ClientIpResolver {

    /**
     * 反向代理传递客户端地址的标准请求头名称。
     */
    private static final String FORWARDED_FOR_HEADER = "X-Forwarded-For";

    /**
     * 来源地址列的最大长度，与数据库列 {@code client_ip VARCHAR(45)} 对齐。
     */
    private static final int MAX_LENGTH = 45;

    /**
     * 工具类不允许实例化。
     */
    private ClientIpResolver() {
    }

    /**
     * 解析当前请求的来源地址。
     *
     * @return 来源地址，无请求上下文或取不到地址时返回 {@code null}
     */
    public static String resolve() {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return null;
        }
        return truncate(firstForwardedAddress(request));
    }

    /**
     * 读取当前线程绑定的 HTTP 请求。
     *
     * @return 当前请求，非 Web 请求上下文或未经过追踪过滤器时返回 {@code null}
     */
    private static HttpServletRequest currentRequest() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletAttributes) {
            return servletAttributes.getRequest();
        }
        return null;
    }

    /**
     * 返回请求头中的首个来源地址，缺失时回退到连接对端地址。
     *
     * @param request 当前请求
     * @return 来源地址，缺失时返回 {@code null}
     */
    private static String firstForwardedAddress(HttpServletRequest request) {
        String forwardedFor = request.getHeader(FORWARDED_FOR_HEADER);
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            String first = forwardedFor.split(",")[0].trim();
            if (!first.isEmpty()) {
                return first;
            }
        }
        return request.getRemoteAddr();
    }

    /**
     * 截断超出列长的地址，避免写入失败。
     *
     * @param address 来源地址，允许为 {@code null}
     * @return 不超过列长的地址，入参为空时返回 {@code null}
     */
    private static String truncate(String address) {
        if (address == null || address.isBlank()) {
            return null;
        }
        String trimmed = address.trim();
        return trimmed.length() <= MAX_LENGTH ? trimmed : trimmed.substring(0, MAX_LENGTH);
    }
}
