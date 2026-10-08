package cn.orangenode.forge.framework.web;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 来源地址解析。
 *
 * <p>位于反向代理之后时取 {@code X-Forwarded-For} 的第一段，否则取连接对端地址。
 * 结果只用于展示与留痕，不参与任何授权判断。</p>
 */
public final class ClientAddresses {

    /**
     * 反向代理传递客户端地址的标准请求头。
     */
    private static final String FORWARDED_FOR_HEADER = "X-Forwarded-For";

    /**
     * 地址最大长度，与数据库 {@code VARCHAR(45)} 列一致。
     */
    private static final int MAX_LENGTH = 45;

    /**
     * 工具类不允许实例化。
     */
    private ClientAddresses() {
    }

    /**
     * 解析请求的来源地址。
     *
     * @param request 当前请求，可为空
     * @return 来源地址，取不到时为 {@code null}
     */
    public static String resolve(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String address = request.getRemoteAddr();
        String forwardedFor = request.getHeader(FORWARDED_FOR_HEADER);
        if (forwardedFor != null && !forwardedFor.split(",")[0].isBlank()) {
            address = forwardedFor.split(",")[0];
        }
        if (address == null || address.isBlank()) {
            return null;
        }
        String trimmed = address.trim();
        return trimmed.length() <= MAX_LENGTH ? trimmed : trimmed.substring(0, MAX_LENGTH);
    }
}
